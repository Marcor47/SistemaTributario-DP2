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
import pe.municipalidad.tributario.contribuyente.DomicilioFiscal;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.common.AccesoDenegadoException;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
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
 * HU006 y HU007: servicio de reportes de Seguimiento Institucional para la Gerencia de Rentas.
 * HU006: generación sin soporte de TI (RF-024, RF-050, RF-051).
 * HU007: guardar, gestionar y exportar vistas personalizadas a Excel o PDF (RF-008, RF-044, RF-059, RF-060).
 */
@Service
public class ReporteService {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteService.class);
    private static final BigDecimal UMBRAL_PRICO_POR_DEFECTO = BigDecimal.valueOf(2000);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /* Filtros que acepta cada reporte, con el nombre del parámetro de la API. */
    private static final Map<TipoReporte, List<String>> FILTROS = Map.of(
            TipoReporte.MOROSOS, List.of("periodo", "zona", "tributo", "regimen", "tipoDocumento", "etapa", "montoMinimo"),
            TipoReporte.PRICOS, List.of("anio", "zona", "tributo", "montoMinimo"),
            TipoReporte.BUENOS_PAGADORES, List.of("anio", "zona", "tributo", "montoMinimo"));

    private final SamnetGateway samnet;
    private final RestriccionReporteRepository restricciones;
    private final SolicitudReporteRepository solicitudes;
    private final VistaReporteRepository vistas;
    private final ReporteExportador exportador;
    private final UsuarioRepository usuarios;
    private final ParametroConfiguracionRepository parametros;
    private final JsonMapper json;
    private final Clock reloj;
    private final LoteETLRepository lotes;

    public ReporteService(SamnetGateway samnet, RestriccionReporteRepository restricciones,
            SolicitudReporteRepository solicitudes, VistaReporteRepository vistas,
            ReporteExportador exportador, UsuarioRepository usuarios,
            ParametroConfiguracionRepository parametros, JsonMapper json, Clock reloj,
            LoteETLRepository lotes) {
        this.samnet = samnet;
        this.restricciones = restricciones;
        this.solicitudes = solicitudes;
        this.vistas = vistas;
        this.exportador = exportador;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.json = json;
        this.reloj = reloj;
        this.lotes = lotes;
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

    /* HU018: zonas visibles para el rol en el reporte indicado, para el selector de la pantalla. */
    @Transactional(readOnly = true)
    public List<String> zonas(TipoReporte tipo, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        Acceso acceso = accesoDe(usuario, tipo);
        if (!acceso.puedeVerReporte()) {
            throw new AccesoDenegadoException("REPORTE_RESTRINGIDO",
                    "Su rol no tiene acceso al " + tipo.getTitulo().toLowerCase() + ".");
        }
        return samnet.obtenerZonas().stream()
                .filter(acceso::puedeVerZona)
                .toList();
    }

    @Transactional
    public ReporteRespuesta<?> generar(TipoReporte tipo, FiltrosReporte filtros, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        return ejecutarReporte(tipo, filtros, usuario, FormatoExportacion.NINGUNO);
    }

    private ReporteRespuesta<?> ejecutarReporte(TipoReporte tipo, FiltrosReporte filtros, Usuario usuario,
            FormatoExportacion formato) {
        Acceso acceso = accesoDe(usuario, tipo);
        if (!acceso.puedeVerReporte()) {
            throw new AccesoDenegadoException("REPORTE_RESTRINGIDO",
                    "Su rol no tiene acceso al " + tipo.getTitulo().toLowerCase() + ".");
        }
        if (tipo == TipoReporte.MOROSOS && filtros.anio() != null && filtros.periodo() == null) {
            throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                    "El reporte de morosos no se filtra por año: incluye la deuda vencida de todos los años.");
        }

        LocalDate fechaCorte = lotes.findTopByEstadoAndFuenteOrderByFechaCorteDescIdLoteDesc(
                        EstadoLote.PUBLICADO, "ps_etl_cobranza")
                .map(lote -> lote.getFechaCorte())
                // La instalación inicial aún no tiene una carga ETL: se conserva el comportamiento previo.
                .orElseGet(() -> LocalDate.now(reloj));
        int anio = Objects.requireNonNullElse(filtros.periodo() != null ? filtros.periodo() : filtros.anio(), fechaCorte.getYear());
        FiltrosReporte aplicados = filtros.conAnio(tipo == TipoReporte.MOROSOS && filtros.periodo() == null ? null : anio);
        List<Cartera> carteras = agrupar(samnet.obtenerCartera(fechaCorte, anio), aplicados).stream()
                .filter(cartera -> aplicados.zona() == null || cartera.zonas().contains(aplicados.zona()))
                .filter(cartera -> cartera.zonas().stream().allMatch(acceso::puedeVerZona))
                .filter(cartera -> tipo != TipoReporte.MOROSOS || aplicados.regimen() == null
                        || aplicados.regimen().equalsIgnoreCase(regimenDe(cartera)))
                .filter(cartera -> tipo != TipoReporte.MOROSOS || aplicados.tipoDocumento() == null
                        || aplicados.tipoDocumento() == cartera.datos().tipoDocumento())
                .filter(cartera -> tipo != TipoReporte.MOROSOS || aplicados.etapa() == null
                        || aplicados.etapa() == etapaCobranza(cartera))
                .toList();

        ReporteRespuesta<?> reporte = switch (tipo) {
            case MOROSOS -> morosos(carteras, aplicados, fechaCorte);
            case PRICOS -> pricos(carteras, aplicados, fechaCorte);
            case BUENOS_PAGADORES -> buenosPagadores(carteras, aplicados, fechaCorte);
        };
        solicitudes.save(new SolicitudReporte(tipo, usuario, LocalDateTime.now(reloj),
                serializar(aplicados), formato));
        return reporte;
    }

        /* ===== HU018: Generar reporte de morosos por zona y periodo (RF-024) ===== */
    @Transactional
    public ReporteRespuesta<?> generarMorososPorZona(FiltrosReporte filtros, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        return ejecutarReporte(TipoReporte.MOROSOS, filtros, usuario, FormatoExportacion.NINGUNO);
    }

    @Transactional
    public ExportacionReporte exportarMorososPorZona(FiltrosReporte filtros, FormatoExportacion formato,
            List<String> columnas, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        ReporteRespuesta<?> reporte = ejecutarReporte(TipoReporte.MOROSOS, filtros, usuario, formato);
        ExportacionReporte exportacion = exportador.exportar(reporte, formato, columnas, usuario.getNombreCompleto());
        String extension = formato == FormatoExportacion.EXCEL ? ".xlsx" : ".pdf";
        return new ExportacionReporte(exportacion.contenido(),
                "reporte_morosos_zona_" + reporte.fechaCorte().toString().replace("-", "") + extension,
                exportacion.mediaType());
    }

    /* ===== HU007: Vistas personalizadas de reporte (RF-008, RF-044, RNF-023) ===== */

    @Transactional
    public VistaReporteRespuesta guardarVista(TipoReporte tipo, GuardarVistaPeticion peticion, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        Acceso acceso = accesoDe(usuario, tipo);
        if (!acceso.puedeVerReporte()) {
            throw new AccesoDenegadoException("REPORTE_RESTRINGIDO",
                    "Su rol no tiene acceso al " + tipo.getTitulo().toLowerCase() + ".");
        }

        String nombreLimpio = peticion.nombre() == null ? "" : peticion.nombre().strip();
        if (nombreLimpio.isBlank()) {
            throw new DatoInvalidoException("NOMBRE_REQUERIDO", "El nombre de la vista es obligatorio.");
        }
        if (vistas.existsByUsuarioAndTipoReporteAndNombre(usuario, tipo, nombreLimpio)) {
            throw new ConflictoException("NOMBRE_VISTA_DUPLICADO",
                    "Ya existe una vista guardada con el nombre '" + nombreLimpio + "' para este reporte.");
        }

        FiltrosReporte filtros = peticion.filtros() != null ? peticion.filtros() : new FiltrosReporte(null, null, null, null);
        if (tipo == TipoReporte.MOROSOS && filtros.anio() != null) {
            throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                    "El reporte de morosos no se filtra por año: incluye la deuda vencida de todos los años.");
        }

        List<String> columnas = peticion.columnasVisibles();
        if (columnas == null || columnas.isEmpty()) {
            throw new DatoInvalidoException("COLUMNAS_REQUERIDAS", "Debe indicar al menos una columna visible.");
        }
        exportador.validarColumnas(tipo, columnas);

        TipoGrafica tipoGrafica = peticion.tipoGrafica() != null ? peticion.tipoGrafica() : TipoGrafica.TABLA;

        VistaReporte vista = new VistaReporte(tipo, usuario, nombreLimpio, tipoGrafica,
                serializar(filtros), serializar(columnas), peticion.orden(), LocalDateTime.now(reloj));
        vistas.save(vista);
        return mapearVista(vista);
    }

    @Transactional(readOnly = true)
    public List<VistaReporteRespuesta> listarVistas(TipoReporte tipo, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        List<VistaReporte> lista = tipo != null
                ? vistas.findByUsuarioAndTipoReporteOrderByFechaCreacionDesc(usuario, tipo)
                : vistas.findByUsuarioOrderByFechaCreacionDesc(usuario);
        return lista.stream().map(this::mapearVista).toList();
    }

    @Transactional(readOnly = true)
    public VistaReporteRespuesta obtenerVista(Long idVista, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        VistaReporte vista = vistaPropia(idVista, usuario);
        return mapearVista(vista);
    }

    @Transactional
    public VistaEjecutadaRespuesta<?> abrirVista(Long idVista, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        VistaReporte vista = vistaPropia(idVista, usuario);
        FiltrosReporte filtros = parsearFiltros(vista.getFiltros());
        ReporteRespuesta<?> reporte = ejecutarReporte(vista.getTipoReporte(), filtros, usuario, FormatoExportacion.NINGUNO);
        return new VistaEjecutadaRespuesta<>(mapearVista(vista), reporte);
    }

    @Transactional
    public VistaReporteRespuesta actualizarVista(Long idVista, ActualizarVistaPeticion peticion, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        VistaReporte vista = vistaPropia(idVista, usuario);

        String nuevoNombre = peticion.nombre() != null ? peticion.nombre().strip() : null;
        if (nuevoNombre != null && !nuevoNombre.isBlank() && !nuevoNombre.equalsIgnoreCase(vista.getNombre())) {
            if (vistas.existsByUsuarioAndTipoReporteAndNombre(usuario, vista.getTipoReporte(), nuevoNombre)) {
                throw new ConflictoException("NOMBRE_VISTA_DUPLICADO",
                        "Ya existe una vista guardada con el nombre '" + nuevoNombre + "' para este reporte.");
            }
        }

        String filtrosJson = vista.getFiltros();
        if (peticion.filtros() != null) {
            if (vista.getTipoReporte() == TipoReporte.MOROSOS && peticion.filtros().anio() != null) {
                throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                        "El reporte de morosos no se filtra por año: incluye la deuda vencida de todos los años.");
            }
            filtrosJson = serializar(peticion.filtros());
        }

        String columnasJson = vista.getColumnasVisibles();
        if (peticion.columnasVisibles() != null) {
            if (peticion.columnasVisibles().isEmpty()) {
                throw new DatoInvalidoException("COLUMNAS_REQUERIDAS", "Debe indicar al menos una columna visible.");
            }
            exportador.validarColumnas(vista.getTipoReporte(), peticion.columnasVisibles());
            columnasJson = serializar(peticion.columnasVisibles());
        }

        vista.actualizar(nuevoNombre, peticion.tipoGrafica(), filtrosJson, columnasJson, peticion.orden());
        return mapearVista(vista);
    }

    @Transactional
    public void eliminarVista(Long idVista, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        VistaReporte vista = vistaPropia(idVista, usuario);
        vistas.delete(vista);
    }

    /* ===== HU007: Exportación a Excel y PDF (RF-008, RF-059, RF-060) ===== */

    @Transactional
    public ExportacionReporte exportar(TipoReporte tipo, FiltrosReporte filtros, FormatoExportacion formato,
            List<String> columnas, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        ReporteRespuesta<?> reporte = ejecutarReporte(tipo, filtros, usuario, formato);
        return exportador.exportar(reporte, formato, columnas, usuario.getNombreCompleto());
    }

    @Transactional
    public ExportacionReporte exportarVista(Long idVista, FormatoExportacion formato, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        VistaReporte vista = vistaPropia(idVista, usuario);
        FiltrosReporte filtros = parsearFiltros(vista.getFiltros());
        List<String> columnas = parsearColumnas(vista.getColumnasVisibles(), vista.getTipoReporte());
        ReporteRespuesta<?> reporte = ejecutarReporte(vista.getTipoReporte(), filtros, usuario, formato);
        return exportador.exportar(reporte, formato, columnas, usuario.getNombreCompleto());
    }

    private VistaReporte vistaPropia(Long idVista, Usuario usuario) {
        return vistas.findById(idVista)
                .filter(v -> v.perteneceA(usuario))
                .orElseThrow(() -> new RecursoNoEncontradoException("VISTA_NO_ENCONTRADA",
                        "No existe esa vista de reporte."));
    }

    private VistaReporteRespuesta mapearVista(VistaReporte vista) {
        return new VistaReporteRespuesta(
                vista.getIdVista(),
                vista.getTipoReporte(),
                vista.getNombre(),
                vista.getTipoGrafica(),
                parsearFiltros(vista.getFiltros()),
                parsearColumnas(vista.getColumnasVisibles(), vista.getTipoReporte()),
                vista.getOrden(),
                vista.getFechaCreacion()
        );
    }

    private FiltrosReporte parsearFiltros(String filtrosJson) {
        if (filtrosJson == null || filtrosJson.isBlank()) {
            return new FiltrosReporte(null, null, null, null);
        }
        try {
            return json.readValue(filtrosJson, FiltrosReporte.class);
        } catch (Exception ex) {
            LOG.warn("No se pudieron parsear los filtros guardados: {}", filtrosJson, ex);
            return new FiltrosReporte(null, null, null, null);
        }
    }

    private List<String> parsearColumnas(String columnasJson, TipoReporte tipo) {
        if (columnasJson == null || columnasJson.isBlank()) {
            return ReporteExportador.COLUMNAS_DEFECTO.getOrDefault(tipo, List.of("codigo", "nombre"));
        }
        try {
            String[] cols = json.readValue(columnasJson, String[].class);
            return List.of(cols);
        } catch (Exception ex) {
            LOG.warn("No se pudieron parsear las columnas guardadas: {}", columnasJson, ex);
            return ReporteExportador.COLUMNAS_DEFECTO.getOrDefault(tipo, List.of("codigo", "nombre"));
        }
    }

    private String serializar(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new IllegalStateException("Error al serializar a JSON", ex);
        }
    }

    /* RF-024 y HU018: cuotas vencidas impagas de cualquier año (o del periodo solicitado); la de mayor deuda primero. */
    private ReporteRespuesta<FilaMoroso> morosos(List<Cartera> carteras, FiltrosReporte filtros,
            LocalDate fechaCorte) {
        boolean filtraPeriodo = filtros.periodo() != null;
        Function<CarteraTributo, BigDecimal> selectorDeuda = filtraPeriodo
                ? CarteraTributo::deudaVencidaAnio
                : CarteraTributo::deudaVencida;

        List<FilaMoroso> filas = carteras.stream()
                .filter(cartera -> cartera.suma(selectorDeuda).signum() > 0)
                .filter(cartera -> alcanza(cartera.suma(selectorDeuda), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(selectorDeuda))
                        .reversed().thenComparing(Cartera::codigo))
                .map(cartera -> filaMoroso(cartera, fechaCorte, filtraPeriodo))
                .toList();
        String concepto = filtraPeriodo ? "Deuda vencida periodo " + filtros.periodo() : "Deuda vencida";
        return respuesta(TipoReporte.MOROSOS, fechaCorte, filtros, concepto, filas, FilaMoroso::deudaVencida,
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

    /* Etapa más avanzada de la deuda vencida; sin etapas registradas se asume mora temprana. */
    private static EtapaCobranza etapaCobranza(Cartera cartera) {
        return cartera.tributos().stream()
                .map(CarteraTributo::etapaCobranza)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(EtapaCobranza.MORA_TEMPRANA);
    }

    /* Régimen del contribuyente; GENERAL es el valor que ya muestra el reporte cuando SAMNET no lo informa. */
    private static String regimenDe(Cartera cartera) {
        return cartera.tributos().stream()
                .map(CarteraTributo::regimen)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("GENERAL");
    }

    private static FilaMoroso filaMoroso(Cartera cartera, LocalDate fechaCorte, boolean filtraPeriodo) {
        LocalDate masAntiguo = cartera.tributos().stream()
                .map(CarteraTributo::vencimientoMasAntiguo)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(fechaCorte);
        EtapaCobranza etapa = etapaCobranza(cartera);
        List<MontoPorTributo> porTributo = cartera.tributos().stream()
                .filter(tributo -> (filtraPeriodo ? tributo.deudaVencidaAnio() : tributo.deudaVencida()).signum() > 0)
                .map(tributo -> new MontoPorTributo(tributo.tributo(), filtraPeriodo ? tributo.deudaVencidaAnio() : tributo.deudaVencida()))
                .toList();

        BigDecimal deuda = filtraPeriodo
                ? cartera.suma(CarteraTributo::deudaVencidaAnio)
                : cartera.suma(CarteraTributo::deudaVencida);
        BigDecimal sinBeneficio = filtraPeriodo
                ? cartera.suma(CarteraTributo::deudaSinBeneficioAnio)
                : cartera.suma(CarteraTributo::deudaSinBeneficio);
        int cuotas = filtraPeriodo
                ? cartera.cuenta(CarteraTributo::cuotasVencidasAnio)
                : cartera.cuenta(CarteraTributo::cuotasVencidas);

        String regimen = regimenDe(cartera);
        DomicilioFiscal domDesglosado = cartera.tributos().stream()
                .map(CarteraTributo::domicilioDesglosado)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new FilaMoroso(
                cartera.codigo(),
                cartera.datos().nombre(),
                cartera.datos().nombre(),
                regimen,
                cartera.datos().tipoDocumento(),
                cartera.datos().numeroDocumento(),
                cartera.datos().condicion(),
                cartera.datos().tipoPersona(),
                cartera.datos().correo(),
                cartera.datos().telefonos(),
                cartera.datos().domicilioFiscal(),
                domDesglosado,
                cartera.zonas(),
                deuda,
                sinBeneficio,
                cuotas,
                masAntiguo,
                ChronoUnit.DAYS.between(masAntiguo, fechaCorte),
                etapa,
                etapa.getNombre(),
                porTributo,
                cartera.tributos().stream().map(CarteraTributo::detalleDeuda)
                        .filter(Objects::nonNull).findFirst().orElse(null)
        );
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
