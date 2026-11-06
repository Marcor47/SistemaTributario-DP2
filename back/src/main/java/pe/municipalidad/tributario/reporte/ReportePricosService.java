package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.analitica.HechoCobranza;
import pe.municipalidad.tributario.analitica.HechoCobranzaRepository;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.ReidentificacionService;
import pe.municipalidad.tributario.etl.TipoEntidad;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Tributo;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.DeudaAnioCorte;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.Documento;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.DomicilioFiscal;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.FilaPricos;
import pe.municipalidad.tributario.reporte.ReportePricosRespuesta.Tributos;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU037 (RF-050): reporte de Principales Contribuyentes. Los montos se calculan sobre el modelo estrella de la
 * última carga publicada de ps_etl_cobranza (RF-008). La identidad, el domicilio y el contacto se completan desde
 * SAMNET usando el puente de reidentificación auditado (RNF-018), porque la capa analítica solo guarda seudónimos.
 *
 * Deuda: insoluto de las cuotas ya vencidas a la fecha de corte. Base imponible: base del predio en dim_predio
 * (no descuenta copropiedad). Cada generación queda registrada como solicitud de reporte (RF-059).
 */
@Service
@Transactional(readOnly = true)
public class ReportePricosService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportePricosService.class);
    private static final String FUENTE_COBRANZA = "ps_etl_cobranza";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final HechoCobranzaRepository hechos;
    private final LoteETLRepository loteETLRepository;
    private final ReidentificacionService reidentificacion;
    private final SamnetGateway samnet;
    private final UsuarioRepository usuarioRepository;
    private final RestriccionReporteRepository restriccionReporteRepository;
    private final SolicitudReporteRepository solicitudReporteRepository;
    private final Clock reloj;

    public ReportePricosService(HechoCobranzaRepository hechos,
            LoteETLRepository loteETLRepository,
            ReidentificacionService reidentificacion,
            SamnetGateway samnet,
            UsuarioRepository usuarioRepository,
            RestriccionReporteRepository restriccionReporteRepository,
            SolicitudReporteRepository solicitudReporteRepository,
            Clock reloj) {
        this.hechos = hechos;
        this.loteETLRepository = loteETLRepository;
        this.reidentificacion = reidentificacion;
        this.samnet = samnet;
        this.usuarioRepository = usuarioRepository;
        this.restriccionReporteRepository = restriccionReporteRepository;
        this.solicitudReporteRepository = solicitudReporteRepository;
        this.reloj = reloj;
    }

    @Transactional
    public ReportePricosRespuesta generar(String nombreUsuario, CriterioPricos criterio, int limite) {
        return generar(nombreUsuario, criterio, limite, FormatoExportacion.NINGUNO);
    }

    @Transactional
    public ReportePricosRespuesta generar(String nombreUsuario, CriterioPricos criterio, int limite,
            FormatoExportacion formato) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
        exigirAcceso(usuario);

        LoteETL lote = loteETLRepository
                .buscarPublicadosConHechos(FUENTE_COBRANZA, Pageable.ofSize(1)).stream()
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("SIN_CARGA_ETL",
                        "No hay una carga publicada del ETL con hechos: no se puede generar el reporte PRICOS."));
        LocalDate fechaCorte = lote.getFechaCorte();
        int anioCorte = fechaCorte.getYear();

        List<HechoCobranza> hechosLote = hechos.buscarPorLote(lote.getIdLote());
        if (hechosLote.isEmpty()) {
            throw new ReglaNegocioException("SIN_CARGA_ETL",
                    "La carga publicada del ETL no tiene hechos de cobranza: no se puede generar el reporte PRICOS.");
        }

        Map<String, Candidato> candidatos = new LinkedHashMap<>();
        for (HechoCobranza hecho : hechosLote) {
            String seudonimo = hecho.getContribuyente().getSeudonimoContribuyente();
            candidatos.computeIfAbsent(seudonimo, Candidato::new).acumular(hecho, anioCorte, fechaCorte);
        }

        List<Candidato> ordenados = candidatos.values().stream().sorted(ordenPara(criterio)).toList();
        List<Candidato> seleccionados = ordenados.stream().limit(limite).toList();
        Map<String, String> claves = reidentificacion.clavesDe(
                seleccionados.stream().map(Candidato::seudonimo).toList(),
                TipoEntidad.CONTRIBUYENTE, usuario);
        if (claves.isEmpty()) {
            throw new ReglaNegocioException("ETL_SIN_CORRESPONDENCIA",
                    "La carga del ETL no tiene correspondencia con SAMNET: no se puede generar el reporte PRICOS.");
        }

        List<FilaPricos> filas = new ArrayList<>();
        for (Candidato candidato : seleccionados) {
            String codigo = claves.get(candidato.seudonimo());
            if (codigo == null) {
                LOG.warn("Se omite un PRICO del ETL: su seudónimo no tiene correspondencia con SAMNET");
                continue;
            }
            Contribuyente contribuyente = samnet.buscarContribuyente(codigo).stream().findFirst().orElse(null);
            if (contribuyente == null) {
                LOG.warn("Se omite un PRICO del ETL: SAMNET no devolvió el contribuyente reidentificado");
                continue;
            }
            filas.add(fila(filas.size() + 1, candidato, contribuyente));
        }

        solicitudReporteRepository.save(new SolicitudReporte(TipoReporte.PRICOS, usuario, LocalDateTime.now(reloj),
                "{\"criterio\":\"" + criterio + "\",\"limite\":" + limite + "}", formato));
        return new ReportePricosRespuesta(lote.getIdLote(), fechaCorte, criterio, limite, candidatos.size(), filas);
    }

    /* RF-060: un rol con el reporte completo restringido no puede generarlo. */
    private void exigirAcceso(Usuario usuario) {
        for (Rol rol : usuario.getRoles()) {
            boolean restringido = restriccionReporteRepository.findByRolAndTipoReporte(rol, TipoReporte.PRICOS)
                    .stream().anyMatch(restriccion -> !restriccion.permite(TipoReporte.PRICOS,
                            DimensionRestringida.REPORTE, null));
            if (restringido) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para este reporte.");
            }
        }
    }

    private static Comparator<Candidato> ordenPara(CriterioPricos criterio) {
        Comparator<Candidato> porCriterio = criterio == CriterioPricos.DEUDA_TOTAL
                ? Comparator.comparing(Candidato::totalDeuda)
                : Comparator.comparing(Candidato::baseImponible);
        return porCriterio.reversed().thenComparing(Candidato::seudonimo);
    }

    private static FilaPricos fila(int posicion, Candidato candidato, Contribuyente contribuyente) {
        var domicilio = contribuyente.getDomicilioFiscal();
        var tipoDocumento = contribuyente.getTipoDocumento();
        var tipoPersona = contribuyente.getTipoPersona();
        String regimen = candidato.regimen != null ? candidato.regimen : contribuyente.getRegimen();
        String condicion = candidato.condicion != null ? candidato.condicion : contribuyente.getCondicion();
        String tipoPersonaEtiqueta = candidato.tipoPersona != null
                ? candidato.tipoPersona
                : (tipoPersona == null ? null : tipoPersona.name());
        return new FilaPricos(
                posicion,
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                regimen,
                new Documento(tipoDocumento == null ? null : tipoDocumento.name(),
                        contribuyente.getNumeroDocumento()),
                new DomicilioFiscal(
                        domicilio == null ? null : domicilio.direccion(),
                        domicilio == null ? null : domicilio.via(),
                        domicilio == null ? null : domicilio.numero(),
                        domicilio == null ? null : domicilio.departamento(),
                        domicilio == null ? null : domicilio.interior(),
                        domicilio == null ? null : domicilio.manzana(),
                        domicilio == null ? null : domicilio.lote(),
                        domicilio == null ? null : domicilio.distrito()),
                candidato.baseImponible,
                new Tributos(candidato.emisionPredial, candidato.emisionArbitrios),
                new Tributos(candidato.deudaAnteriorPredial, candidato.deudaAnteriorArbitrios),
                new DeudaAnioCorte(candidato.deudaActualPredial, candidato.deudaActualArbitrios,
                        candidato.descuentoArbitrios),
                condicion,
                tipoPersonaEtiqueta,
                contribuyente.getCorreo(),
                contribuyente.getTelefonos());
    }

    private static BigDecimal monto(BigDecimal valor) {
        return valor != null ? valor : CERO;
    }

    /* Acumulado de un contribuyente a partir de los hechos de la carga. */
    private static final class Candidato {

        private final String seudonimo;
        private final Set<Long> predios = new HashSet<>();
        private String regimen;
        private String condicion;
        private String tipoPersona;
        private BigDecimal baseImponible = CERO;
        private BigDecimal emisionPredial = CERO;
        private BigDecimal emisionArbitrios = CERO;
        private BigDecimal deudaAnteriorPredial = CERO;
        private BigDecimal deudaAnteriorArbitrios = CERO;
        private BigDecimal deudaActualPredial = CERO;
        private BigDecimal deudaActualArbitrios = CERO;
        private BigDecimal descuentoArbitrios = CERO;

        private Candidato(String seudonimo) {
            this.seudonimo = seudonimo;
        }

        private String seudonimo() {
            return seudonimo;
        }

        private BigDecimal baseImponible() {
            return baseImponible;
        }

        private BigDecimal totalDeuda() {
            return deudaAnteriorPredial.add(deudaAnteriorArbitrios)
                    .add(deudaActualPredial).add(deudaActualArbitrios);
        }

        private void acumular(HechoCobranza hecho, int anioCorte, LocalDate fechaCorte) {
            if (regimen == null && hecho.getContribuyente() != null) {
                regimen = hecho.getContribuyente().getRegimen();
                condicion = hecho.getContribuyente().getCondicion();
                tipoPersona = hecho.getContribuyente().getTipoPersona() == null
                        ? null
                        : hecho.getContribuyente().getTipoPersona().name();
            }
            boolean predial = hecho.getTributo() == Tributo.IMPUESTO_PREDIAL;
            BigDecimal emitido = monto(hecho.getMontoEmitido());
            BigDecimal insoluto = monto(hecho.getMontoInsoluto());
            BigDecimal descuento = monto(hecho.getMontoDescuento());

            if (hecho.getPredio() != null && predios.add(hecho.getPredio().getIdPredio())) {
                baseImponible = baseImponible.add(monto(hecho.getPredio().getBaseImponible()));
            }

            /* La emisión es de todo el año, aunque la cuota todavía no venza o ya esté pagada. */
            if (hecho.getAnio() == anioCorte) {
                if (predial) {
                    emisionPredial = emisionPredial.add(emitido);
                } else {
                    emisionArbitrios = emisionArbitrios.add(emitido);
                }
            }

            /* La deuda solo suma cuotas vencidas al corte que todavía conservan insoluto. */
            if (!hecho.getFechaVencimiento().isBefore(fechaCorte) || insoluto.signum() <= 0) {
                return;
            }
            if (hecho.getAnio() < anioCorte) {
                if (predial) {
                    deudaAnteriorPredial = deudaAnteriorPredial.add(insoluto);
                } else {
                    deudaAnteriorArbitrios = deudaAnteriorArbitrios.add(insoluto);
                }
            } else if (hecho.getAnio() == anioCorte) {
                if (predial) {
                    deudaActualPredial = deudaActualPredial.add(insoluto);
                } else {
                    deudaActualArbitrios = deudaActualArbitrios.add(insoluto);
                    BigDecimal antesDelDescuento = insoluto.add(descuento);
                    if (antesDelDescuento.signum() > 0) {
                        BigDecimal porcentaje = descuento.multiply(CIEN)
                                .divide(antesDelDescuento, 2, RoundingMode.HALF_UP);
                        descuentoArbitrios = descuentoArbitrios.max(porcentaje);
                    }
                }
            }
        }
    }
}
