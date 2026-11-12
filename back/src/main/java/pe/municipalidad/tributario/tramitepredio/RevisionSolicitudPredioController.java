package pe.municipalidad.tributario.tramitepredio;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU043 (RF-056): bandeja de Ventanilla para revisar las solicitudes de registro o traspaso de predio. Requiere el
 * permiso SOLICITUDES_PREDIO:REVISAR; el revisor se toma del usuario interno autenticado.
 */
@RestController
@RequestMapping("/api/gestion/solicitudes-predio")
public class RevisionSolicitudPredioController {

    private final RevisionSolicitudPredioService servicio;

    public RevisionSolicitudPredioController(RevisionSolicitudPredioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<SolicitudPredioBandejaRespuesta> listar(
            @RequestParam(required = false) EstadoSolicitudPredio estado) {
        return servicio.listar(estado);
    }

    @GetMapping("/{idSolicitud}")
    public SolicitudPredioRevisionRespuesta obtener(@PathVariable Long idSolicitud) {
        return servicio.obtener(idSolicitud);
    }

    /* Se entrega inline para que la pantalla muestre el PDF o la imagen sin descargarlo. */
    @GetMapping("/{idSolicitud}/adjuntos/{idAdjunto}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long idSolicitud, @PathVariable Long idAdjunto) {
        SolicitudPredioService.DescargaAdjunto archivo = servicio.descargar(idSolicitud, idAdjunto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("adjunto-" + idAdjunto).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(archivo.tipo().tipoMime()))
                .body(archivo.contenido());
    }

    @PostMapping("/{idSolicitud}/aceptacion")
    public SolicitudPredioRevisionRespuesta aceptar(@PathVariable Long idSolicitud,
            @Valid @RequestBody AceptarSolicitudPredio aceptacion, Principal revisor) {
        return servicio.aceptar(idSolicitud, aceptacion, revisor.getName());
    }

    @PostMapping("/{idSolicitud}/rechazo")
    public SolicitudPredioRevisionRespuesta rechazar(@PathVariable Long idSolicitud,
            @Valid @RequestBody RechazarSolicitudPredio rechazo, Principal revisor) {
        return servicio.rechazar(idSolicitud, rechazo.motivo(), revisor.getName());
    }
}
