package pe.municipalidad.tributario.reclamo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reclamos")
public class ReclamoController {
    private final ReclamoService servicio;

    public ReclamoController(ReclamoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mios")
    public List<ReclamoRespuesta> listarPropios(HttpServletRequest request) {
        return servicio.listarPropios(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReclamoRespuesta registrar(@Valid @RequestBody RegistrarReclamoSolicitud solicitud,
            HttpServletRequest request) {
        return servicio.registrar(solicitud, request);
    }

    @PostMapping("/mios/{idReclamo}/cancelar")
    public ReclamoRespuesta cancelarPropio(@org.springframework.web.bind.annotation.PathVariable Long idReclamo,
            HttpServletRequest request) {
        return servicio.cancelarPropio(idReclamo, request);
    }

    @GetMapping("/gestion")
    public List<ReclamoGestionRespuesta> listarParaGestion(HttpServletRequest request) {
        return servicio.listarParaGestion(request);
    }

    @GetMapping("/catalogos")
    public CatalogosReclamoRespuesta catalogos(HttpServletRequest request) {
        return servicio.catalogos(request);
    }

    @PostMapping("/gestion/{idReclamo}/derivaciones")
    public ReclamoGestionRespuesta derivar(
            @org.springframework.web.bind.annotation.PathVariable Long idReclamo,
            @Valid @RequestBody DerivarReclamoSolicitud solicitud, HttpServletRequest request) {
        return servicio.derivar(idReclamo, solicitud, request);
    }

    @GetMapping("/asignados")
    public List<ReclamoGestionRespuesta> listarAsignados(HttpServletRequest request) {
        return servicio.listarAsignados(request);
    }

    @PostMapping("/asignados/{idReclamo}/respuesta")
    public ReclamoGestionRespuesta responder(
            @org.springframework.web.bind.annotation.PathVariable Long idReclamo,
            @Valid @RequestBody ResponderReclamoSolicitud solicitud, HttpServletRequest request) {
        return servicio.responder(idReclamo, solicitud, request);
    }
}
