package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.analitica.HechoCobranza;
import pe.municipalidad.tributario.analitica.HechoCobranzaRepository;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.DomicilioFiscal;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.ReidentificacionService;
import pe.municipalidad.tributario.etl.TipoEntidad;
import pe.municipalidad.tributario.gestiontributaria.CarteraTributo;
import pe.municipalidad.tributario.gestiontributaria.DetalleDeuda;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.Tributo;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Camino híbrido del reporte de morosos (HU006, HU018, RF-024): los montos y la antigüedad salen del
 * modelo estrella (analitica.hecho_cobranza) de la última carga publicada; la identidad y la etapa de
 * cobranza se completan desde SAMNET a través del puente de reidentificación (RNF-018). Así el reporte
 * conserva las cifras del ETL y no necesita guardar PII en la capa analítica.
 */
@Service
public class CarteraEtlService {

    private static final Logger LOG = LoggerFactory.getLogger(CarteraEtlService.class);

    private final HechoCobranzaRepository hechos;
    private final ReidentificacionService reidentificacion;
    private final SamnetGateway samnet;

    public CarteraEtlService(HechoCobranzaRepository hechos, ReidentificacionService reidentificacion,
            SamnetGateway samnet) {
        this.hechos = hechos;
        this.reidentificacion = reidentificacion;
        this.samnet = samnet;
    }

    /*
     * Devuelve la cartera del lote. Si el lote no tiene correspondencia con SAMNET no se puede mostrar
     * la identidad: se informa como regla de negocio en lugar de devolver filas incompletas.
     */
    @Transactional
    public List<CarteraTributo> obtener(LoteETL lote, int anioDesde, int anioHasta, FiltroBeneficio beneficio,
            String zona, Usuario usuario) {
        List<HechoCobranza> filtrados = hechos
                .buscarVencidasConSaldo(lote.getIdLote(), lote.getFechaCorte()).stream()
                .filter(hecho -> aplicaBeneficio(hecho, beneficio))
                .filter(hecho -> aplicaZona(hecho, zona))
                .toList();
        if (filtrados.isEmpty()) {
            return List.of();
        }

        Map<Grupo, List<HechoCobranza>> grupos = filtrados.stream()
                .collect(Collectors.groupingBy(
                        hecho -> new Grupo(hecho.getContribuyente().getSeudonimoContribuyente(), hecho.getTributo()),
                        LinkedHashMap::new, Collectors.toList()));

        Set<String> seudonimosContribuyente = grupos.keySet().stream().map(Grupo::seudonimo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> seudonimosPredio = filtrados.stream().map(h -> h.getPredio().getSeudonimoPredio())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> codigosContribuyente = reidentificacion.clavesDe(seudonimosContribuyente,
                TipoEntidad.CONTRIBUYENTE, usuario);
        if (codigosContribuyente.isEmpty()) {
            throw new ReglaNegocioException("ETL_SIN_CORRESPONDENCIA",
                    "La carga del ETL no tiene correspondencia con SAMNET: no se puede generar el reporte.");
        }
        Map<String, String> codigosPredio = reidentificacion.clavesDe(seudonimosPredio, TipoEntidad.PREDIO, usuario);

        Map<String, Enriquecimiento> cache = new HashMap<>();
        List<CarteraTributo> carteras = new ArrayList<>();
        for (Map.Entry<Grupo, List<HechoCobranza>> entrada : grupos.entrySet()) {
            String codigo = codigosContribuyente.get(entrada.getKey().seudonimo());
            if (codigo == null) {
                LOG.warn("Se omite un moroso del ETL: su seudónimo no tiene correspondencia con SAMNET");
                continue;
            }
            Enriquecimiento enriquecimiento = cache.computeIfAbsent(codigo, this::enriquecer);
            if (enriquecimiento.contribuyente() == null) {
                LOG.warn("Se omite un moroso del ETL: SAMNET no devolvió el contribuyente reidentificado");
                continue;
            }
            carteras.add(aCartera(entrada.getKey().tributo(), entrada.getValue(), enriquecimiento, codigosPredio,
                    anioDesde, anioHasta, lote.getFechaCorte()));
        }
        return carteras;
    }

    /*
     * Con filtro de zona, solo los arbitrios de esa zona tienen una ubicación geográfica. El predial es
     * del contribuyente y no se atribuye a una zona.
     */
    private static boolean aplicaZona(HechoCobranza hecho, String zona) {
        return zona == null || (hecho.getTributo() == Tributo.ARBITRIOS
                && hecho.getPredio().getZona().equalsIgnoreCase(zona));
    }

    /* HU018: zonas que trae la carga, para el selector del reporte de morosos. */
    @Transactional(readOnly = true)
    public List<String> zonas(LoteETL lote) {
        return hechos.listarZonas(lote.getIdLote());
    }

    private static boolean aplicaBeneficio(HechoCobranza hecho, FiltroBeneficio beneficio) {
        return switch (beneficio) {
            case AMBOS -> true;
            case CON_BENEFICIO -> monto(hecho.getMontoDescuento()).signum() > 0;
            case SIN_BENEFICIO -> monto(hecho.getMontoDescuento()).signum() == 0;
        };
    }

    private Enriquecimiento enriquecer(String codigo) {
        Contribuyente contribuyente = samnet.buscarContribuyente(codigo).stream().findFirst().orElse(null);
        if (contribuyente == null) {
            return new Enriquecimiento(null, List.of(), Map.of());
        }
        List<Predio> predios = samnet.buscarPredio(null, codigo);
        EstadoCuenta estado = samnet.obtenerEstadoCuenta(codigo);
        Map<ClaveObligacion, EtapaCobranza> etapas = new HashMap<>();
        for (Obligacion obligacion : estado.getObligaciones()) {
            etapas.put(new ClaveObligacion(obligacion.getCodigoPredio(), obligacion.getTributo(),
                    obligacion.getAnio(), obligacion.getCuota()), obligacion.getEtapaCobranza());
        }
        return new Enriquecimiento(contribuyente, predios, etapas);
    }

    private static CarteraTributo aCartera(Tributo tributo, List<HechoCobranza> grupo, Enriquecimiento enriquecimiento,
            Map<String, String> codigosPredio, int anioDesde, int anioHasta, LocalDate fechaCorte) {
        Contribuyente contribuyente = enriquecimiento.contribuyente();
        HechoCobranza referencia = grupo.getFirst();

        BigDecimal deudaVencida = BigDecimal.ZERO;
        BigDecimal deudaSinBeneficio = BigDecimal.ZERO;
        BigDecimal deudaVencidaAnio = BigDecimal.ZERO;
        BigDecimal deudaSinBeneficioAnio = BigDecimal.ZERO;
        BigDecimal emitidoAnio = BigDecimal.ZERO;
        BigDecimal pagadoAnio = BigDecimal.ZERO;
        int cuotasVencidas = 0;
        int cuotasVencidasAnio = 0;
        int cuotasExigiblesAnio = 0;
        LocalDate vencimientoMasAntiguo = null;
        EtapaCobranza etapa = null;
        List<DetalleDeuda> detalle = new ArrayList<>();

        for (HechoCobranza hecho : grupo) {
            BigDecimal insoluto = monto(hecho.getMontoInsoluto());
            BigDecimal descuento = monto(hecho.getMontoDescuento());
            BigDecimal sinBeneficio = insoluto.add(descuento);
            boolean enRango = hecho.getAnio() >= anioDesde && hecho.getAnio() <= anioHasta;

            if (enRango) {
                emitidoAnio = emitidoAnio.add(monto(hecho.getMontoEmitido()));
                pagadoAnio = pagadoAnio.add(monto(hecho.getMontoPagado()));
                cuotasExigiblesAnio++;
            }

            String codigoPredio = codigosPredio.get(hecho.getPredio().getSeudonimoPredio());
            EtapaCobranza etapaHecho = etapaDe(enriquecimiento.etapas(), codigoPredio, hecho);
            if (etapaHecho != null && (etapa == null || etapaHecho.compareTo(etapa) > 0)) {
                etapa = etapaHecho;
            }
            detalle.add(new DetalleDeuda(
                    hecho.getAnio(),
                    codigoPredio,
                    hecho.getTributo() == Tributo.ARBITRIOS ? hecho.getPredio().getZona() : null,
                    hecho.getTributo(),
                    hecho.getCuota(),
                    sinBeneficio,
                    insoluto,
                    hecho.getFechaVencimiento(),
                    (int) ChronoUnit.DAYS.between(hecho.getFechaVencimiento(), fechaCorte),
                    etapaHecho,
                    monto(hecho.getMontoEmitido()),
                    monto(hecho.getMontoPagado()),
                    descuento));

            if (insoluto.signum() > 0) {
                deudaVencida = deudaVencida.add(insoluto);
                deudaSinBeneficio = deudaSinBeneficio.add(sinBeneficio);
                cuotasVencidas++;
                if (vencimientoMasAntiguo == null || hecho.getFechaVencimiento().isBefore(vencimientoMasAntiguo)) {
                    vencimientoMasAntiguo = hecho.getFechaVencimiento();
                }
                if (enRango) {
                    deudaVencidaAnio = deudaVencidaAnio.add(insoluto);
                    deudaSinBeneficioAnio = deudaSinBeneficioAnio.add(sinBeneficio);
                    cuotasVencidasAnio++;
                }
            }
        }
        detalle.sort(Comparator.comparing(DetalleDeuda::anio)
                .thenComparing(DetalleDeuda::tributo)
                .thenComparing(DetalleDeuda::periodo));

        DomicilioFiscal domicilio = contribuyente.getDomicilioFiscal();
        String regimen = contribuyente.getRegimen() != null ? contribuyente.getRegimen()
                : referencia.getContribuyente().getRegimen();
        return new CarteraTributo(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getTipoPersona() != null ? contribuyente.getTipoPersona()
                        : referencia.getContribuyente().getTipoPersona(),
                contribuyente.getTipoDocumento(),
                contribuyente.getNumeroDocumento(),
                contribuyente.getCondicion(),
                contribuyente.getCorreo(),
                contribuyente.getTelefonos(),
                domicilio != null ? domicilio.direccion() : null,
                zonas(enriquecimiento.predios(), grupo),
                tributo,
                emitidoAnio,
                pagadoAnio,
                cuotasExigiblesAnio,
                // La puntualidad necesita la fecha de cada pago: no se usa en morosos y no está en el hecho.
                0,
                deudaVencida,
                cuotasVencidas,
                vencimientoMasAntiguo != null ? vencimientoMasAntiguo : fechaCorte,
                etapa,
                regimen,
                domicilio,
                deudaSinBeneficio,
                deudaVencidaAnio,
                deudaSinBeneficioAnio,
                cuotasVencidasAnio,
                detalle);
    }

    /* Zonas de los predios del contribuyente, igual que la unión de titularidad y obligaciones de SAMNET. */
    private static List<String> zonas(List<Predio> predios, List<HechoCobranza> hechos) {
        Set<String> zonas = new TreeSet<>();
        for (Predio predio : predios) {
            if (predio.getZona() != null) {
                zonas.add(predio.getZona());
            }
        }
        for (HechoCobranza hecho : hechos) {
            zonas.add(hecho.getPredio().getZona());
        }
        return List.copyOf(zonas);
    }

    /*
     * La etapa vive en la obligación de SAMNET. El Impuesto Predial no tiene predio en SAMNET, así que se
     * intenta primero la clave con el predio del hecho y luego la clave sin predio.
     */
    private static EtapaCobranza etapaDe(Map<ClaveObligacion, EtapaCobranza> etapas, String codigoPredio,
            HechoCobranza hecho) {
        EtapaCobranza etapa = etapas.get(new ClaveObligacion(codigoPredio, hecho.getTributo(), hecho.getAnio(),
                hecho.getCuota()));
        if (etapa == null && hecho.getTributo() == Tributo.IMPUESTO_PREDIAL) {
            etapa = etapas.get(new ClaveObligacion(null, hecho.getTributo(), hecho.getAnio(), hecho.getCuota()));
        }
        return etapa;
    }

    private static BigDecimal monto(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private record Grupo(String seudonimo, Tributo tributo) {
    }

    private record ClaveObligacion(String codigoPredio, Tributo tributo, int anio, int cuota) {
    }

    private record Enriquecimiento(Contribuyente contribuyente, List<Predio> predios,
            Map<ClaveObligacion, EtapaCobranza> etapas) {
    }
}
