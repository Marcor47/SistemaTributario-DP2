package pe.municipalidad.tributario.estadocuenta;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.auth.SesionContribuyente;

/** HU014: el contribuyente solo consulta la cuenta asociada a su sesión. */
@RestController
@RequestMapping("/api/portal/estado-cuenta")
public class EstadoCuentaPortalController {
    private final EstadoCuentaService servicio;

    public EstadoCuentaPortalController(EstadoCuentaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public EstadoCuentaRespuesta obtener(HttpServletRequest request) {
        return servicio.obtenerEstadoCuenta(SesionContribuyente.codigoContribuyente(request));
    }
}
