package pe.municipalidad.tributario.pagos;

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

/** HU022: pagos del contribuyente autenticado. */
@RestController
@RequestMapping("/api/portal/pagos")
public class PagoPortalController {
    private final PagoPortalService servicio;

    public PagoPortalController(PagoPortalService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<PagoRespuesta> iniciar(@Valid @RequestBody PagoSolicitud solicitud,
            HttpServletRequest request) {
        PagoRespuesta respuesta = servicio.iniciar(SesionContribuyente.codigoContribuyente(request), solicitud);
        return ResponseEntity.created(URI.create("/api/portal/pagos/" + respuesta.idTransaccion())).body(respuesta);
    }

    @GetMapping("/{id}")
    public PagoRespuesta consultar(@PathVariable Long id, HttpServletRequest request) {
        return servicio.consultar(SesionContribuyente.codigoContribuyente(request), id);
    }

    @PostMapping("/{id}/confirmacion")
    public PagoRespuesta confirmar(@PathVariable Long id, HttpServletRequest request) {
        return servicio.confirmar(SesionContribuyente.codigoContribuyente(request), id);
    }
}
