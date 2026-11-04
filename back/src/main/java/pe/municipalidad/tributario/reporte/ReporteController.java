package pe.municipalidad.tributario.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.obligacion.Tributo;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/*
 * HU006, HU007, HU018 y HU038: controlador de reportes de Seguimiento institucional para la Gerencia de Rentas.
 * HU006: catálogo y generación de reportes sin soporte de TI (back/HU006_API.md).
 * HU007: guardar, gestionar y exportar vistas personalizadas a Excel o PDF (back/HU007_API.md).
 * HU018: reporte de contribuyentes morosos por zona y periodo (RF-024 adaptado).
 * HU038: reporte de buenos pagadores por zona y periodo (RF-051).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final NombreRol[] ROLES_REPORTES = {NombreRol.GERENCIA, NombreRol.ANALISTA_RENTAS};

    private final ReporteService servicio;
    private final SesionInterna sesion;

    public ReporteController(ReporteService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    /* ===== HU006: Catálogo y generación de reportes ===== */

    @GetMapping
    public List<ReporteCatalogoRespuesta> catalogo(HttpServletRequest request) {
        return servicio.catalogo(sesion.conRol(request, ROLES_REPORTES));
    }

    @GetMapping("/zonas")
    public List<String> zonas(@RequestParam TipoReporte tipo, HttpServletRequest request) {
        return servicio.zonas(tipo, sesion.conRol(request, ROLES_REPORTES));
    }

    @GetMapping("/{tipo}")
    public ReporteRespuesta<?> generar(
            @PathVariable TipoReporte tipo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) Integer periodo,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @Size(max = 40, message = "es demasiado largo") String regimen,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) EtapaCobranza etapa,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioDesde,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioHasta,
            @RequestParam(required = false) FiltroBeneficio beneficio,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.generar(tipo, new FiltrosReporte(anio, zona, tributo, montoMinimo, periodo,
                regimen, tipoDocumento, etapa, anioDesde, anioHasta, beneficio), usuario);
    }

    /* ===== HU018: Reporte de morosos por zona y periodo (RF-024) ===== */

    @GetMapping("/morosos-zona")
    public ReporteRespuesta<?> morososPorZona(
            @RequestParam(required = false) Integer periodo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @Size(max = 40, message = "es demasiado largo") String regimen,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) EtapaCobranza etapa,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioDesde,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioHasta,
            @RequestParam(required = false) FiltroBeneficio beneficio,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.generarMorososPorZona(new FiltrosReporte(anio, zona, tributo, montoMinimo,
                periodo, regimen, tipoDocumento, etapa, anioDesde, anioHasta, beneficio), usuario);
    }

    /* ===== HU038: Reporte de buenos pagadores por zona y periodo (RF-051) ===== */

    @GetMapping("/buenos-pagadores-zona")
    public ReporteRespuesta<?> buenosPagadoresPorZona(
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer periodo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        Integer periodoEfectivo = periodo != null ? periodo : anio;
        return servicio.generarBuenosPagadoresPorZona(
                new FiltrosReporte(periodoEfectivo, zona, tributo, montoMinimo, periodoEfectivo), usuario);
    }

    /* ===== HU007: Vistas personalizadas de reporte ===== */

    @PostMapping("/{tipo}/vistas")
    @ResponseStatus(HttpStatus.CREATED)
    public VistaReporteRespuesta guardarVista(
            @PathVariable TipoReporte tipo,
            @Valid @RequestBody GuardarVistaPeticion peticion,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.guardarVista(tipo, peticion, usuario);
    }

    @GetMapping("/{tipo}/vistas")
    public List<VistaReporteRespuesta> listarVistasPorTipo(
            @PathVariable TipoReporte tipo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.listarVistas(tipo, usuario);
    }

    @GetMapping("/vistas")
    public List<VistaReporteRespuesta> listarVistas(
            @RequestParam(required = false) TipoReporte tipo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.listarVistas(tipo, usuario);
    }

    @GetMapping("/vistas/{idVista}")
    public VistaReporteRespuesta obtenerVista(
            @PathVariable Long idVista,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.obtenerVista(idVista, usuario);
    }

    @GetMapping("/vistas/{idVista}/abrir")
    public VistaEjecutadaRespuesta<?> abrirVista(
            @PathVariable Long idVista,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.abrirVista(idVista, usuario);
    }

    @PutMapping("/vistas/{idVista}")
    public VistaReporteRespuesta actualizarVista(
            @PathVariable Long idVista,
            @Valid @RequestBody ActualizarVistaPeticion peticion,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.actualizarVista(idVista, peticion, usuario);
    }

    @DeleteMapping("/vistas/{idVista}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarVista(
            @PathVariable Long idVista,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        servicio.eliminarVista(idVista, usuario);
    }

    /* ===== HU007, HU018 y HU038: Exportación a Excel y PDF ===== */

    @GetMapping("/{tipo}/exportar")
    public ResponseEntity<byte[]> exportar(
            @PathVariable TipoReporte tipo,
            @RequestParam FormatoExportacion formato,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) Integer periodo,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @Size(max = 40, message = "es demasiado largo") String regimen,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) EtapaCobranza etapa,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioDesde,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioHasta,
            @RequestParam(required = false) FiltroBeneficio beneficio,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            @RequestParam(required = false) List<String> columnas,
            @RequestParam(required = false) List<String> columnasDetalle,
            @RequestParam(required = false) @Size(max = 250, message = "es demasiado largo") String orden,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        ExportacionReporte exportacion = servicio.exportar(tipo,
                new FiltrosReporte(anio, zona, tributo, montoMinimo, periodo,
                        regimen, tipoDocumento, etapa, anioDesde, anioHasta, beneficio), formato, columnas,
                columnasDetalle, orden, usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportacion.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(exportacion.mediaType()))
                .body(exportacion.contenido());
    }

    @GetMapping("/morosos-zona/exportar")
    public ResponseEntity<byte[]> exportarMorososPorZona(
            @RequestParam FormatoExportacion formato,
            @RequestParam(required = false) Integer periodo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @Size(max = 40, message = "es demasiado largo") String regimen,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) EtapaCobranza etapa,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioDesde,
            @RequestParam(required = false) @Min(value = 1900, message = "debe ser 1900 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anioHasta,
            @RequestParam(required = false) FiltroBeneficio beneficio,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            @RequestParam(required = false) List<String> columnas,
            @RequestParam(required = false) List<String> columnasDetalle,
            @RequestParam(required = false) @Size(max = 250, message = "es demasiado largo") String orden,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        ExportacionReporte exportacion = servicio.exportarMorososPorZona(
                new FiltrosReporte(anio, zona, tributo, montoMinimo, periodo,
                        regimen, tipoDocumento, etapa, anioDesde, anioHasta, beneficio),
                formato, columnas, columnasDetalle, orden, usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportacion.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(exportacion.mediaType()))
                .body(exportacion.contenido());
    }

    @GetMapping("/buenos-pagadores-zona/exportar")
    public ResponseEntity<byte[]> exportarBuenosPagadoresPorZona(
            @RequestParam FormatoExportacion formato,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer periodo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            @RequestParam(required = false) List<String> columnas,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        Integer periodoEfectivo = periodo != null ? periodo : anio;
        ExportacionReporte exportacion = servicio.exportarBuenosPagadoresPorZona(
                new FiltrosReporte(periodoEfectivo, zona, tributo, montoMinimo, periodoEfectivo),
                formato, columnas, usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportacion.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(exportacion.mediaType()))
                .body(exportacion.contenido());
    }

    @GetMapping("/vistas/{idVista}/exportar")
    public ResponseEntity<byte[]> exportarVista(
            @PathVariable Long idVista,
            @RequestParam FormatoExportacion formato,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        ExportacionReporte exportacion = servicio.exportarVista(idVista, formato, usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportacion.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(exportacion.mediaType()))
                .body(exportacion.contenido());
    }
}
