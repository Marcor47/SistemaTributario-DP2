package pe.municipalidad.tributario.reporteria;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.reporte.ExportacionReporte;
import pe.municipalidad.tributario.reporte.FormatoExportacion;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/*
 * Plataforma analítica de Rentas: indicadores, gráficos y rankings por periodo, zona, tributo y segmento, con vistas
 * guardadas, exportación y asistente de IA (RF-008, RF-012, RF-044, RF-058, RNF-003). Contrato en back/REPORTERIA_API.md.
 */
@RestController
@RequestMapping("/api/reporteria")
public class ReporteriaController {

    private static final NombreRol[] ROLES = {NombreRol.GERENCIA, NombreRol.ANALISTA_RENTAS};

    private final ReporteriaService reporteria;
    private final VistaAnaliticaService vistas;
    private final ExportacionReporteriaService exportador;
    private final AsistenteReporteriaService asistente;
    private final SesionInterna sesion;

    public ReporteriaController(ReporteriaService reporteria, VistaAnaliticaService vistas,
            ExportacionReporteriaService exportador, AsistenteReporteriaService asistente, SesionInterna sesion) {
        this.reporteria = reporteria;
        this.vistas = vistas;
        this.exportador = exportador;
        this.asistente = asistente;
        this.sesion = sesion;
    }

    @GetMapping("/catalogo")
    public CatalogoReporteriaRespuesta catalogo(HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return reporteria.catalogo();
    }

    @GetMapping("/datos")
    public DatosReporteriaRespuesta datos(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth desde,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth hasta,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) @Size(max = 25, message = "es demasiado largo") String tributo,
            @RequestParam(required = false) @Size(max = 80, message = "es demasiado largo") String segmento,
            @RequestParam(required = false) @Size(max = 60, message = "es demasiado largo") String uso,
            @RequestParam(required = false) @Min(value = 0, message = "no es válido") Integer contribuyente,
            @RequestParam(required = false) @Min(value = 0, message = "no es válido") Integer destacado,
            HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return reporteria.datos(new CuboReporteria.Filtro(desde, hasta, zona, tributo, segmento, uso, contribuyente),
                destacado);
    }

    @GetMapping("/vistas")
    public List<VistaAnaliticaRespuesta> vistas(HttpServletRequest request) {
        return vistas.listar(sesion.conRol(request, ROLES));
    }

    @PostMapping("/vistas")
    public VistaAnaliticaRespuesta guardarVista(@Valid @RequestBody GuardarVistaAnaliticaPeticion peticion,
            HttpServletRequest request) {
        return vistas.guardar(peticion, sesion.conRol(request, ROLES));
    }

    @DeleteMapping("/vistas/{idVista}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarVista(@PathVariable Long idVista, HttpServletRequest request) {
        vistas.eliminar(idVista, sesion.conRol(request, ROLES));
    }

    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam FormatoExportacion formato,
            @Valid @RequestBody ExportarVistaPeticion peticion, HttpServletRequest request) {
        ExportacionReporte exportacion = exportador.exportar(peticion, formato, sesion.conRol(request, ROLES));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportacion.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(exportacion.mediaType()))
                .body(exportacion.contenido());
    }

    @GetMapping("/asistente/estado")
    public ModeloLenguajeCliente.Salud estadoAsistente(HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return asistente.estado();
    }

    @PostMapping("/asistente/preguntas")
    public RespuestaAsistente preguntar(@Valid @RequestBody PreguntaAsistentePeticion peticion,
            HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return asistente.preguntar(peticion);
    }
}
