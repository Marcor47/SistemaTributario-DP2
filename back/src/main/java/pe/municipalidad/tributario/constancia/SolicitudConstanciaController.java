package pe.municipalidad.tributario.constancia;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.auth.SesionContribuyente;

/*
 * HU015: firmar digitalmente la solicitud de Constancia de No Adeudo desde la app. El contribuyente se toma
 * de la sesión, nunca de la petición (RNF-023).
 */
@RestController
@RequestMapping("/api/constancias")
public class SolicitudConstanciaController {

    private final SolicitudConstanciaService solicitudConstanciaService;

    public SolicitudConstanciaController(SolicitudConstanciaService solicitudConstanciaService) {
        this.solicitudConstanciaService = solicitudConstanciaService;
    }

    @PostMapping
    public ResponseEntity<SolicitudConstanciaRespuesta> iniciar(HttpServletRequest request) {
        SolicitudConstanciaRespuesta solicitud =
                solicitudConstanciaService.iniciarSolicitud(SesionContribuyente.codigoContribuyente(request));
        return ResponseEntity.created(URI.create("/api/constancias/" + solicitud.idSolicitud())).body(solicitud);
    }

    @GetMapping("/{idSolicitud}")
    public SolicitudConstanciaRespuesta obtener(@PathVariable Long idSolicitud, HttpServletRequest request) {
        return solicitudConstanciaService.obtenerSolicitud(SesionContribuyente.codigoContribuyente(request),
                idSolicitud);
    }

    @GetMapping("/{idSolicitud}/documento-firma")
    public DocumentoFirmaRespuesta obtenerDocumentoParaFirma(@PathVariable Long idSolicitud,
            HttpServletRequest request) {
        return solicitudConstanciaService.obtenerDocumentoParaFirma(SesionContribuyente.codigoContribuyente(request),
                idSolicitud);
    }

    @PostMapping("/{idSolicitud}/firma")
    public SolicitudConstanciaRespuesta firmar(@PathVariable Long idSolicitud,
            @Valid @RequestBody FirmaSolicitudConstancia firma, HttpServletRequest request) {
        return solicitudConstanciaService.firmarSolicitud(SesionContribuyente.codigoContribuyente(request),
                idSolicitud, firma);
    }
}
