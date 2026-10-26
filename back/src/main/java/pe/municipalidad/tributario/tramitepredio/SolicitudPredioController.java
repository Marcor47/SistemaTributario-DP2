package pe.municipalidad.tributario.tramitepredio;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

@RestController
@RequestMapping("/api/portal/solicitudes-predio")
public class SolicitudPredioController {
    private final SolicitudPredioService servicio;

    public SolicitudPredioController(SolicitudPredioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/requisitos")
    public List<RequisitoRespuesta> requisitos(@RequestParam TipoTramitePredio tipoTramite,
            @RequestParam TipoPredio tipoPredio, HttpServletRequest request) {
        SesionContribuyente.codigoContribuyente(request);
        return servicio.requisitos(tipoTramite, tipoPredio);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SolicitudPredioRespuesta> enviar(@RequestParam TipoTramitePredio tipoTramite,
            @RequestParam TipoPredio tipoPredio, @RequestParam(required = false) String codigoPredio,
            @RequestParam List<Long> requisitoIds, @RequestParam List<MultipartFile> archivos,
            HttpServletRequest request) {
        SolicitudPredioRespuesta respuesta = servicio.enviar(SesionContribuyente.codigoContribuyente(request),
                tipoTramite, tipoPredio, codigoPredio, requisitoIds, archivos);
        return ResponseEntity.created(URI.create("/api/portal/solicitudes-predio/" + respuesta.idSolicitud()))
                .body(respuesta);
    }

    @GetMapping
    public List<SolicitudPredioRespuesta> listar(HttpServletRequest request) {
        return servicio.listar(SesionContribuyente.codigoContribuyente(request));
    }

    @GetMapping("/{id}")
    public SolicitudPredioRespuesta obtener(@PathVariable Long id, HttpServletRequest request) {
        return servicio.obtener(SesionContribuyente.codigoContribuyente(request), id);
    }

    @GetMapping("/{id}/adjuntos/{idAdjunto}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, @PathVariable Long idAdjunto,
            HttpServletRequest request) {
        SolicitudPredioService.DescargaAdjunto archivo = servicio.descargar(
                SesionContribuyente.codigoContribuyente(request), id, idAdjunto);
        String tipo = switch (archivo.tipo()) {
            case PDF -> "application/pdf";
            case JPG -> "image/jpeg";
            case PNG -> "image/png";
        };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=adjunto-" + idAdjunto).contentType(MediaType.parseMediaType(tipo))
                .body(archivo.contenido());
    }
}
