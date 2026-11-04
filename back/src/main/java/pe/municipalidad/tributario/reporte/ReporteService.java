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
import java.util.Set;
import java.util.stream.Collectors;
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
import pe.municipalidad.tributario.gestiontributaria.DetalleDeuda;
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
    private static final Set<String> COLUMNAS_DETALLE_MOROSOS = Set.of(
            "anio", "codigoPredio", "tributo", "periodo", "fechaVencimiento", "diasVencido",
            "etapaCobranza", "montoSinBeneficio", "montoConBeneficio");

    /* Filtros que acepta cada reporte, con el nombre del parámetro de la API. */
    private static final Map<TipoReporte, List<String>> FILTROS = Map.of(
            TipoReporte.MOROSOS, List.of("anioDesde", "anioHasta", "zona", "tributo", "regimen",
                    "tipoDocumento", "etapa", "beneficio", "montoMinimo"),
            TipoReporte.PRICOS, List.of("anio", "zona", "tributo", "montoMinimo"),
            TipoReporte.BUENOS_PAGADORES, List.of("periodo", "anio", "zona", "tributo", "montoMinimo"));

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
        if (tipo == TipoReporte.MOROSOS && filtros.anio() != null && filtros.periodo() == null
                && filtros.anioDesde() == null) {
            throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                    "El reporte de morosos usa el rango anioDesde/anioHasta.");
        }

        LocalDate fechaCorte = lotes.findTopByEstadoAndFuenteOrderByFechaCorteDescIdLoteDesc(
                        EstadoLote.PUBLICADO, "ps_etl_cobranza")
                .map(lote -> lote.getFechaCorte())
                // La instalación inicial aún no tiene una carga ETL: se conserva el comportamiento previo.
                .orElseGet(() -> LocalDate.now(reloj));
        int anioCorte = fechaCorte.getYear();
        int anio = Objects.requireNonNullElse(filtros.periodo() != null ? filtros.periodo() : filtros.anio(), anioCorte);
        boolean esMorosos = tipo == TipoReporte.MOROSOS;
        int anioDesde;
        int anioHasta;
        if (esMorosos && filtros.periodo() != null) {
            anioDesde = filtros.periodo();
            anioHasta = filtros.periodo();
        } else if (esMorosos) {
            anioDesde = Objects.requireNonNullElse(filtros.anioDesde(), 1900);
            anioHasta = Objects.requireNonNullElse(filtros.anioHasta(), anioCorte);
        } else {
            anioDesde = anio;
            anioHasta = anio;
        }
        if (anioDesde > anioHasta) {
            throw new DatoInvalidoException("RANGO_INVALIDO",
                    "El año inicial no puede ser mayor que el año final.");
        }
        FiltroBeneficio beneficio = esMorosos
                ? Objects.requireNonNullElse(filtros.beneficio(), FiltroBeneficio.AMBOS)
                : FiltroBeneficio.AMBOS;
        FiltrosReporte aplicados = filtros.conRangoYBeneficio(esMorosos ? null : anio, anioDesde, anioHasta, beneficio);
        List<CarteraTributo> datosSamnet = samnet.obtenerCartera(fechaCorte, anioDesde, anioHasta,
                beneficio.name());
        // La puntualidad es global: un filtro de tributo no debe ocultar deuda de otro tributo.
        Set<String> contribuyentesPuntuales = tipo == TipoReporte.BUENOS_PAGADORES && aplicados.tributo() != null
                ? agrupar(datosSamnet, new FiltrosReporte(anio, null, null, null)).stream()
                        .filter(ReporteService::esBuenPagador)
                        .map(Cartera::codigo)
                        .collect(Collectors.toSet())
                : null;
        List<Cartera> carteras = agrupar(datosSamnet, aplicados).stream()
                .filter(cartera -> contribuyentesPuntuales == null
                        || contribuyentesPuntuales.contains(cartera.codigo()))
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
            List<String> columnas, List<String> columnasDetalle, String orden, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        List<String> detalle = columnasDetalle != null ? columnasDetalle : List.of();
        validarColumnasDetalle(detalle);
        ReporteRespuesta<?> reporte = ejecutarReporte(TipoReporte.MOROSOS, filtros, usuario, formato);
        ExportacionReporte exportacion = exportador.exportar(reporte, formato, columnas,
                detalle, orden, usuario.getNombreCompleto());
        String extension = formato == FormatoExportacion.EXCEL ? ".xlsx" : ".pdf";
        return new ExportacionReporte(exportacion.contenido(),
                "reporte_morosos_zona_" + reporte.fechaCorte().toString().replace("-", "") + extension,
                exportacion.mediaType());
    }

    /* ===== HU038: Generar reporte de buenos pagadores por zona (RF-051) ===== */
    @Transactional
    public ReporteRespuesta<?> generarBuenosPagadoresPorZona(FiltrosReporte filtros, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        return ejecutarReporte(TipoReporte.BUENOS_PAGADORES, filtros, usuario, FormatoExportacion.NINGUNO);
    }

    @Transactional
    public ExportacionReporte exportarBuenosPagadoresPorZona(FiltrosReporte filtros, FormatoExportacion formato,
            List<String> columnas, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        ReporteRespuesta<?> reporte = ejecutarReporte(TipoReporte.BUENOS_PAGADORES, filtros, usuario, formato);
        ExportacionReporte exportacion = exportador.exportar(reporte, formato, columnas, usuario.getNombreCompleto());
        String extension = formato == FormatoExportacion.EXCEL ? ".xlsx" : ".pdf";
        return new ExportacionReporte(exportacion.contenido(),
                "reporte_buenos_pagadores_zona_" + reporte.fechaCorte().toString().replace("-", "") + extension,
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
        Optional<VistaReporte> vistaExistente = vistas.findByUsuarioAndTipoReporteAndNombre(usuario, tipo, nombreLimpio);

        FiltrosReporte filtros = peticion.filtros() != null ? peticion.filtros() : new FiltrosReporte(null, null, null, null);
        if (tipo == TipoReporte.MOROSOS && filtros.anio() != null && filtros.periodo() == null
                && filtros.anioDesde() == null) {
            throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                    "El reporte de morosos usa el rango anioDesde/anioHasta.");
        }
        validarRango(filtros);

        List<String> columnas = peticion.columnasVisibles();
        if (columnas == null || columnas.isEmpty()) {
            throw new DatoInvalidoException("COLUMNAS_REQUERIDAS", "Debe indicar al menos una columna visible.");
        }
        exportador.validarColumnas(tipo, columnas);
        List<String> columnasDetalle = tipo == TipoReporte.MOROSOS
                ? peticion.columnasDetalleVisibles() : List.of();
        if (columnasDetalle == null) {
            columnasDetalle = List.of();
        }
        validarColumnasDetalle(columnasDetalle);
        List<String> ordenColumnas = peticion.ordenColumnas() != null
                ? peticion.ordenColumnas() : columnas;
        exportador.validarColumnas(tipo, ordenColumnas);
        List<String> ordenColumnasDetalle = tipo == TipoReporte.MOROSOS
                ? (peticion.ordenColumnasDetalle() != null ? peticion.ordenColumnasDetalle() : columnasDetalle)
                : List.of();
        validarColumnasDetalle(ordenColumnasDetalle);

        TipoGrafica tipoGrafica = peticion.tipoGrafica() != null ? peticion.tipoGrafica() : TipoGrafica.TABLA;

        if (vistaExistente.isPresent()) {
            VistaReporte vista = vistaExistente.get();
            vista.actualizar(nombreLimpio, tipoGrafica, serializar(filtros), serializar(columnas),
                    serializar(columnasDetalle), serializar(ordenColumnas),
                    serializar(ordenColumnasDetalle), peticion.orden());
            vistas.save(vista);
            return mapearVista(vista);
        }

        VistaReporte vista = new VistaReporte(tipo, usuario, nombreLimpio, tipoGrafica,
                serializar(filtros), serializar(columnas), serializar(columnasDetalle),
                serializar(ordenColumnas), serializar(ordenColumnasDetalle), peticion.orden(),
                LocalDateTime.now(reloj));
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
            if (vista.getTipoReporte() == TipoReporte.MOROSOS && peticion.filtros().anio() != null
                    && peticion.filtros().periodo() == null && peticion.filtros().anioDesde() == null) {
                throw new DatoInvalidoException("FILTRO_NO_APLICABLE",
                        "El reporte de morosos usa el rango anioDesde/anioHasta.");
            }
            validarRango(peticion.filtros());
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

        String columnasDetalleJson = vista.getColumnasDetalleVisibles();
        if (peticion.columnasDetalleVisibles() != null) {
            List<String> columnasDetalle = vista.getTipoReporte() == TipoReporte.MOROSOS
                    ? peticion.columnasDetalleVisibles() : List.of();
            validarColumnasDetalle(columnasDetalle);
            columnasDetalleJson = serializar(columnasDetalle);
        }

        String ordenColumnasJson = vista.getOrdenColumnas();
        if (peticion.ordenColumnas() != null) {
            exportador.validarColumnas(vista.getTipoReporte(), peticion.ordenColumnas());
            ordenColumnasJson = serializar(peticion.ordenColumnas());
        }

        String ordenColumnasDetalleJson = vista.getOrdenColumnasDetalle();
        if (peticion.ordenColumnasDetalle() != null) {
            List<String> ordenDetalle = vista.getTipoReporte() == TipoReporte.MOROSOS
                    ? peticion.ordenColumnasDetalle() : List.of();
            validarColumnasDetalle(ordenDetalle);
            ordenColumnasDetalleJson = serializar(ordenDetalle);
        }

        vista.actualizar(nuevoNombre, peticion.tipoGrafica(), filtrosJson, columnasJson, columnasDetalleJson,
                ordenColumnasJson, ordenColumnasDetalleJson, peticion.orden());
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
            List<String> columnas, List<String> columnasDetalle, String orden, String nombreUsuario) {
        if (formato == null || formato == FormatoExportacion.NINGUNO) {
            throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        }
        Usuario usuario = usuario(nombreUsuario);
        List<String> detalle = tipo == TipoReporte.MOROSOS && columnasDetalle != null
                ? columnasDetalle : List.of();
        validarColumnasDetalle(detalle);
        ReporteRespuesta<?> reporte = ejecutarReporte(tipo, filtros, usuario, formato);
        return exportador.exportar(reporte, formato, columnas, detalle, orden, usuario.getNombreCompleto());
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
        List<String> columnasDetalle = parsearColumnasDetalle(vista.getColumnasDetalleVisibles());
        ReporteRespuesta<?> reporte = ejecutarReporte(vista.getTipoReporte(), filtros, usuario, formato);
        return exportador.exportar(reporte, formato, columnas, columnasDetalle, vista.getOrden(),
                usuario.getNombreCompleto());
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
                parsearColumnasDetalle(vista.getColumnasDetalleVisibles()),
                parsearLista(vista.getOrdenColumnas(), parsearColumnas(vista.getColumnasVisibles(), vista.getTipoReporte())),
                parsearLista(vista.getOrdenColumnasDetalle(), parsearColumnasDetalle(vista.getColumnasDetalleVisibles())),
                vista.getOrden(),
                vista.getFechaCreacion()
        );
    }

    private List<String> parsearLista(String valorJson, List<String> valorPorDefecto) {
        if (valorJson == null || valorJson.isBlank()) {
            return valorPorDefecto;
        }
        try {
            String[] valores = json.readValue(valorJson, String[].class);
            return List.of(valores);
        } catch (Exception ex) {
            LOG.warn("No se pudo parsear la lista guardada: {}", valorJson, ex);
            return valorPorDefecto;
        }
    }

    private static void validarRango(FiltrosReporte filtros) {
        if (filtros.anioDesde() != null && filtros.anioHasta() != null
                && filtros.anioDesde() > filtros.anioHasta()) {
            throw new DatoInvalidoException("RANGO_INVALIDO",
                    "El año inicial no puede ser mayor que el año final.");
        }
    }

    private static void validarColumnasDetalle(List<String> columnas) {
        for (String columna : columnas) {
            if (!COLUMNAS_DETALLE_MOROSOS.contains(columna)) {
                throw new DatoInvalidoException("COLUMNA_NO_VALIDA",
                        "La columna de detalle '" + columna + "' no es válida.");
            }
        }
    }

    private List<String> parsearColumnasDetalle(String columnasJson) {
        if (columnasJson == null || columnasJson.isBlank()) {
            return List.of();
        }
        try {
            String[] cols = json.readValue(columnasJson, String[].class);
            return List.of(cols);
        } catch (Exception ex) {
            LOG.warn("No se pudieron parsear las columnas del detalle: {}", columnasJson, ex);
            return List.of();
        }
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

    /* RF-024 y HU018: cuotas vencidas impagas del rango de años solicitado; la de mayor deuda primero. */
    private ReporteRespuesta<FilaMoroso> morosos(List<Cartera> carteras, FiltrosReporte filtros,
            LocalDate fechaCorte) {
        boolean filtraPeriodo = filtros.periodo() != null;
        boolean filtraRango = filtros.anioDesde() != null || filtros.anioHasta() != null;
        boolean usarRango = filtraPeriodo || filtraRango;
        Function<CarteraTributo, BigDecimal> selectorDeuda = usarRango
                ? CarteraTributo::deudaVencidaAnio
                : CarteraTributo::deudaVencida;
        List<FilaMoroso> filas = carteras.stream()
                .filter(cartera -> cartera.suma(selectorDeuda).signum() > 0)
                .filter(cartera -> alcanza(cartera.suma(selectorDeuda), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(selectorDeuda))
                        .reversed().thenComparing(Cartera::codigo))
                .map(cartera -> filaMoroso(cartera, fechaCorte, usarRango))
                .toList();
        String concepto = filtraPeriodo ? "Deuda vencida periodo " + filtros.periodo() : "Deuda vencida";
        return respuesta(TipoReporte.MOROSOS, fechaCorte, filtros, concepto, filas,
                FilaMoroso::deudaVencida, null);
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
                .filter(ReporteService::esBuenPagador)
                .filter(cartera -> alcanza(cartera.suma(CarteraTributo::pagadoAnio), filtros.montoMinimo()))
                .sorted(Comparator.comparing((Cartera cartera) -> cartera.suma(CarteraTributo::pagadoAnio))
                        .reversed().thenComparing(Cartera::codigo))
                .map(ReporteService::filaBuenPagador)
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

    private static boolean esBuenPagador(Cartera cartera) {
        return cartera.cuenta(CarteraTributo::cuotasExigiblesAnio) > 0
                && cartera.cuenta(CarteraTributo::cuotasATiempoAnio)
                        == cartera.cuenta(CarteraTributo::cuotasExigiblesAnio)
                && cartera.suma(CarteraTributo::deudaVencida).signum() == 0;
    }

    private static FilaBuenPagador filaBuenPagador(Cartera cartera) {
        BigDecimal emitidoPredial = cartera.tributos().stream()
                .filter(t -> t.tributo() == pe.municipalidad.tributario.obligacion.Tributo.IMPUESTO_PREDIAL)
                .map(CarteraTributo::emitidoAnio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal emitidoArbitrios = cartera.tributos().stream()
                .filter(t -> t.tributo() == pe.municipalidad.tributario.obligacion.Tributo.ARBITRIOS)
                .map(CarteraTributo::emitidoAnio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagadoPredial = cartera.tributos().stream()
                .filter(t -> t.tributo() == pe.municipalidad.tributario.obligacion.Tributo.IMPUESTO_PREDIAL)
                .map(CarteraTributo::pagadoAnio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagadoArbitrios = cartera.tributos().stream()
                .filter(t -> t.tributo() == pe.municipalidad.tributario.obligacion.Tributo.ARBITRIOS)
                .map(CarteraTributo::pagadoAnio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEmitido = emitidoPredial.add(emitidoArbitrios);
        BigDecimal totalPagado = pagadoPredial.add(pagadoArbitrios);

        String regimen = cartera.tributos().stream()
                .map(CarteraTributo::regimen)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("GENERAL");

        DomicilioFiscal domDesglosado = cartera.tributos().stream()
                .map(CarteraTributo::domicilioDesglosado)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new FilaBuenPagador(
                cartera.codigo(),
                cartera.datos().nombre(),
                cartera.datos().nombre(),
                regimen,
                cartera.datos().tipoDocumento(),
                cartera.datos().numeroDocumento(),
                cartera.datos().domicilioFiscal(),
                domDesglosado,
                cartera.zonas(),
                emitidoPredial,
                emitidoArbitrios,
                pagadoPredial,
                pagadoArbitrios,
                totalEmitido,
                totalPagado,
                cartera.cuenta(CarteraTributo::cuotasATiempoAnio),
                totalPagado
        );
    }

    private static FilaMoroso filaMoroso(Cartera cartera, LocalDate fechaCorte, boolean usarRango) {
        LocalDate masAntiguo = cartera.tributos().stream()
                .map(CarteraTributo::vencimientoMasAntiguo)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(fechaCorte);
        EtapaCobranza etapa = etapaCobranza(cartera);
        List<MontoPorTributo> porTributo = cartera.tributos().stream()
                .filter(tributo -> (usarRango ? tributo.deudaVencidaAnio() : tributo.deudaVencida()).signum() > 0)
                .map(tributo -> new MontoPorTributo(tributo.tributo(),
                        usarRango ? tributo.deudaVencidaAnio() : tributo.deudaVencida()))
                .toList();

        BigDecimal deuda = cartera.suma(usarRango
                ? CarteraTributo::deudaVencidaAnio : CarteraTributo::deudaVencida);
        BigDecimal sinBeneficio = cartera.suma(usarRango
                ? CarteraTributo::deudaSinBeneficioAnio : CarteraTributo::deudaSinBeneficio);
        int cuotas = cartera.cuenta(usarRango
                ? CarteraTributo::cuotasVencidasAnio : CarteraTributo::cuotasVencidas);

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
                detalleDeuda(cartera)
        );
    }

    /* Une el detalle de todos los tributos del contribuyente, sin perder Predial ni Arbitrios. */
    private static List<DetalleDeuda> detalleDeuda(Cartera cartera) {
        return cartera.tributos().stream()
                .flatMap(tributo -> tributo.detalleDeuda() == null ? java.util.stream.Stream.empty()
                        : tributo.detalleDeuda().stream())
                .sorted(Comparator.comparing(DetalleDeuda::anio)
                        .thenComparing(DetalleDeuda::tributo)
                        .thenComparing(DetalleDeuda::periodo))
                .toList();
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
