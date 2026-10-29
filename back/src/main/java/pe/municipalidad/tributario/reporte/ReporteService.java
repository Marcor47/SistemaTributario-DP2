package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.common.AccesoDenegadoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.gestiontributaria.CarteraTributo;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaBuenPagador;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaMoroso;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaPrico;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.MontoPorTributo;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.Resumen;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracion;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracionRepository;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

/*
 * HU006: la Gerencia genera los reportes de morosos, PRICOS y buenos pagadores sin pedirlos a TI (RF-024, RF-050,
 * RF-051). Los datos se leen de SAMNET a la fecha de corte de hoy; cada generación queda registrada (RF-059) y se
 * aplican las restricciones del rol sobre el reporte y sus zonas (RF-060).
 */
@Service
public class ReporteService {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteService.class);
    private static final BigDecimal UMBRAL_PRICO_POR_DEFECTO = BigDecimal.valueOf(2000);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /* Filtros que acepta cada reporte, con el nombre del parámetro de la API. */
    private static final Map<TipoReporte, List<String>> FILTROS = Map.of(
            TipoReporte.MOROSOS, List.of("zona", "tributo", "montoMinimo"),
            TipoReporte.PRICOS, List.of("anio", "zona", "tributo", "montoMinimo"),
            TipoReporte.BUENOS_PAGADORES, List.of("anio", "zona", "tributo", "montoMinimo"));

    private final SamnetGateway samnet;
    private final RestriccionReporteRepository restricciones;
    private final SolicitudReporteRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final ParametroConfiguracionRepository parametros;
    private final JsonMapper json;
    private final Clock reloj;

    public ReporteService(SamnetGateway samnet, RestriccionReporteRepository restricciones,
            SolicitudReporteRepository solicitudes, UsuarioRepository usuarios,
            ParametroConfiguracionRepository parametros, JsonMapper json, Clock reloj) {
        this.samnet = samnet;
        this.restricciones = restricciones;
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.json = json;
        this.reloj = reloj;
    }

    /* Catálogo sin los reportes que el rol tiene restringidos por completo. */
    @Transactional(readOnly = true)
    public List<ReporteCatalogoRespuesta> catalogo(String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        return Arrays.stream(TipoReporte.values())
                .filter(tipo -> accesoDe(usuario, tipo).puedeVerReporte())
                .map(tipo -> new ReporteCatalogoRespuesta(tipo, tipo.getTitulo(), tipo.getDescripcion(),
                        FILTROS.get(tipo)))
                .toList();
    }

    @Transactional
    public ReporteRespuesta<?> generar(TipoReporte tipo, FiltrosReporte filtros, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        Acceso acceso = accesoDe(usuario, tipo);
        if (!acceso.puedeVerReporte()) {
            throw new AccesoDenegadoException("REPORTE_RESTRINGIDO",
                    "Su rol no tiene acceso al " + tipo.getTitulo().toLowerCase() + ".");
        }
        if (tipo == TipoReporte.MOROSOS && filtros.anio() != null) {
            throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                    "El reporte de morosos no se filtra por año: incluye la deuda vencida de todos los años.");
        }

        LocalDate fechaCorte = LocalDate.now(reloj);
        int anio = Objects.requireNonNullElse(filtros.anio(), fechaCorte.getYear());
        FiltrosReporte aplicados = filtros.conAnio(tipo == TipoReporte.MOROSOS ? null : anio);
        List<Cartera> carteras = agrupar(samnet.obtenerCartera(fechaCorte, anio), aplicados).stream()
                .filter(cartera -> aplicados.zona() == null || cartera.zonas().contains(aplicados.zona()))
                .filter(cartera -> cartera.zonas().stream().allMatch(acceso::puedeVerZona))
                .toList();

        ReporteRespuesta<?> reporte = switch (tipo) {
            case MOROSOS -> morosos(carteras, aplicados, fechaCorte);
            case PRICOS -> pricos(carteras, aplicados, fechaCorte);
            case BUENOS_PAGADORES -> buenosPagadores(carteras, aplicados, fechaCorte);
        };
        solicitudes.save(new SolicitudReporte(tipo, usuario, LocalDateTime.now(reloj),
                json.writeValueAsString(aplicados), FormatoExportacion.NINGUNO));
        return reporte;
    }

    /* RF-024: cuotas vencidas impagas de cualquier año; la de mayor deuda primero. */
    private ReporteRespuesta<FilaMoroso> morosos(List<Cartera> carteras, FiltrosReporte filtros,
            LocalDate fechaCorte) {
        List<FilaMoroso> filas = carteras.stream()
                .filter(cartera -> cartera.suma(CarteraTributo::deudaVencida).signum() > 0)
                .filter(cartera -> alcanza(cartera.suma(CarteraTributo::deudaVencida), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(CarteraTributo::deudaVencida))
                        .reversed().thenComparing(Cartera::codigo))
                .map(cartera -> filaMoroso(cartera, fechaCorte))
                .toList();
        return respuesta(TipoReporte.MOROSOS, fechaCorte, filtros, "Deuda vencida", filas, FilaMoroso::deudaVencida,
                null);
    }

    /* RF-050: monto emitido en el año igual o mayor al umbral (y al monto mínimo, si se pide). */
    private ReporteRespuesta<FilaPrico> pricos(List<Cartera> carteras, FiltrosReporte filtros, LocalDate fechaCorte) {
        BigDecimal umbral = umbralPrico();
        List<FilaPrico> filas = carteras.stream()
                .filter(cartera -> alcanza(cartera.suma(CarteraTributo::emitidoAnio), umbral))
                .filter(cartera -> alcanza(cartera.suma(CarteraTributo::emitidoAnio), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(CarteraTributo::emitidoAnio))
                        .reversed().thenComparing(Cartera::codigo))
                .map(ReporteService::filaPrico)
                .toList();
        return respuesta(TipoReporte.PRICOS, fechaCorte, filtros, "Monto emitido en " + filtros.anio(), filas,
                FilaPrico::emitidoAnio, umbral);
    }

    /* RF-051: todas las cuotas vencidas del año pagadas a tiempo y ninguna deuda vencida de ningún año. */
    private ReporteRespuesta<FilaBuenPagador> buenosPagadores(List<Cartera> carteras, FiltrosReporte filtros,
            LocalDate fechaCorte) {
        List<FilaBuenPagador> filas = carteras.stream()
                .filter(cartera -> cartera.cuenta(CarteraTributo::cuotasExigiblesAnio) > 0
                        && cartera.cuenta(CarteraTributo::cuotasATiempoAnio)
                                == cartera.cuenta(CarteraTributo::cuotasExigiblesAnio)
                        && cartera.suma(CarteraTributo::deudaVencida).signum() == 0)
                .filter(cartera -> alcanza(cartera.suma(CarteraTributo::pagadoAnio), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(CarteraTributo::pagadoAnio))
                        .reversed().thenComparing(Cartera::codigo))
                .map(cartera -> new FilaBuenPagador(cartera.codigo(), cartera.datos().nombre(),
                        cartera.datos().tipoDocumento(), cartera.datos().numeroDocumento(),
                        cartera.datos().domicilioFiscal(), cartera.zonas(),
                        cartera.cuenta(CarteraTributo::cuotasATiempoAnio), cartera.suma(CarteraTributo::pagadoAnio)))
                .toList();
        return respuesta(TipoReporte.BUENOS_PAGADORES, fechaCorte, filtros, "Monto pagado en " + filtros.anio(),
                filas, FilaBuenPagador::pagadoAnio, null);
    }

    private static FilaMoroso filaMoroso(Cartera cartera, LocalDate fechaCorte) {
        LocalDate masAntiguo = cartera.tributos().stream()
                .map(CarteraTributo::vencimientoMasAntiguo)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        /* Sin etapas registradas en SAMNET, la deuda está en mora temprana desde su vencimiento. */
        EtapaCobranza etapa = cartera.tributos().stream()
                .map(CarteraTributo::etapaCobranza)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(EtapaCobranza.MORA_TEMPRANA);
        List<MontoPorTributo> porTributo = cartera.tributos().stream()
                .filter(tributo -> tributo.deudaVencida().signum() > 0)
                .map(tributo -> new MontoPorTributo(tributo.tributo(), tributo.deudaVencida()))
                .toList();
        return new FilaMoroso(cartera.codigo(), cartera.datos().nombre(), cartera.datos().tipoDocumento(),
                cartera.datos().numeroDocumento(), cartera.datos().domicilioFiscal(), cartera.zonas(),
                cartera.suma(CarteraTributo::deudaVencida), cartera.cuenta(CarteraTributo::cuotasVencidas),
                masAntiguo, ChronoUnit.DAYS.between(masAntiguo, fechaCorte), etapa, etapa.getNombre(), porTributo);
    }

    private static FilaPrico filaPrico(Cartera cartera) {
        BigDecimal emitido = cartera.suma(CarteraTributo::emitidoAnio);
        BigDecimal pagado = cartera.suma(CarteraTributo::pagadoAnio);
        /* Con umbral 0 puede entrar un contribuyente sin emisión en el año. */
        BigDecimal porcentaje = emitido.signum() == 0
                ? BigDecimal.ZERO
                : pagado.multiply(CIEN).divide(emitido, 2, RoundingMode.HALF_UP);
        return new FilaPrico(cartera.codigo(), cartera.datos().nombre(), cartera.datos().tipoPersona(),
                cartera.datos().tipoDocumento(), cartera.datos().numeroDocumento(), cartera.datos().domicilioFiscal(),
                cartera.zonas(), emitido, pagado, porcentaje, cartera.suma(CarteraTributo::deudaVencida));
    }

    private static <T> ReporteRespuesta<T> respuesta(TipoReporte tipo, LocalDate fechaCorte, FiltrosReporte filtros,
            String conceptoMonto, List<T> filas, Function<T, BigDecimal> monto, BigDecimal umbralPrico) {
        BigDecimal total = filas.stream().map(monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReporteRespuesta<>(tipo, tipo.getTitulo(), fechaCorte, filtros,
                new Resumen(filas.size(), conceptoMonto, total, umbralPrico), filas);
    }

    /* Une las filas de cada contribuyente (una por tributo), dejando solo el tributo pedido. */
    private static List<Cartera> agrupar(List<CarteraTributo> filas, FiltrosReporte filtros) {
        Map<String, List<CarteraTributo>> porContribuyente = new LinkedHashMap<>();
        filas.stream()
                .filter(fila -> filtros.tributo() == null || fila.tributo() == filtros.tributo())
                .forEach(fila -> porContribuyente.computeIfAbsent(fila.codigoContribuyente(),
                        codigo -> new ArrayList<>()).add(fila));
        return porContribuyente.values().stream().map(Cartera::new).toList();
    }

    private static boolean alcanza(BigDecimal monto, BigDecimal minimo) {
        return minimo == null || monto.compareTo(minimo) >= 0;
    }

    /* RF-050: el umbral es configurable sin tocar código (parámetro UMBRAL_PRICO). */
    private BigDecimal umbralPrico() {
        return parametros.findById(ParametroConfiguracion.UMBRAL_PRICO)
                .map(ParametroConfiguracion::getValor)
                .flatMap(ReporteService::comoMonto)
                .orElse(UMBRAL_PRICO_POR_DEFECTO);
    }

    private static Optional<BigDecimal> comoMonto(String valor) {
        try {
            BigDecimal monto = new BigDecimal(valor.strip());
            if (monto.signum() >= 0) {
                return Optional.of(monto);
            }
        } catch (NumberFormatException ex) {
            LOG.warn("El parámetro {} no es numérico; se usa el umbral por defecto",
                    ParametroConfiguracion.UMBRAL_PRICO);
        }
        return Optional.empty();
    }

    private Usuario usuario(String nombreUsuario) {
        return usuarios.findByNombreUsuario(nombreUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
    }

    private Acceso accesoDe(Usuario usuario, TipoReporte tipo) {
        return new Acceso(tipo, usuario.getRoles().stream()
                .map(rol -> restricciones.findByRolAndTipoReporte(rol, tipo))
                .toList());
    }

    /*
     * Restricciones de cada rol del usuario sobre un reporte. Con varios roles basta que uno lo permita; un rol sin
     * restricciones lo ve todo.
     */
    private record Acceso(TipoReporte tipo, List<List<RestriccionReporte>> porRol) {

        boolean puedeVerReporte() {
            return permite(DimensionRestringida.REPORTE, null);
        }

        boolean puedeVerZona(String zona) {
            return permite(DimensionRestringida.ZONA, zona);
        }

        private boolean permite(DimensionRestringida dimension, String valor) {
            return porRol.stream().anyMatch(reglas -> reglas.stream()
                    .allMatch(regla -> regla.permite(tipo, dimension, valor)));
        }
    }

    /* Filas de un contribuyente, una por tributo; los datos de identificación son los mismos en todas. */
    private record Cartera(List<CarteraTributo> tributos) {

        CarteraTributo datos() {
            return tributos.getFirst();
        }

        String codigo() {
            return datos().codigoContribuyente();
        }

        List<String> zonas() {
            return datos().zonas();
        }

        BigDecimal suma(Function<CarteraTributo, BigDecimal> monto) {
            return tributos.stream().map(monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        int cuenta(ToIntFunction<CarteraTributo> cuotas) {
            return tributos.stream().mapToInt(cuotas).sum();
        }
    }
}
