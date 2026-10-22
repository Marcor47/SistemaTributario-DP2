package pe.municipalidad.tributario.estadocuenta;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU004 - API de Estado de Cuenta desglosado por tributo y periodo.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/estado-cuenta/contribuyentes?q=  busqueda libre (codigo, nombre, doc).</li>
 *   <li>GET /api/estado-cuenta/{codigo}           estado de cuenta completo.</li>
 * </ul>
 *
 * <p>El CORS para http://localhost:5173 ya esta configurado en CorsConfig.</p>
 */
@RestController
@RequestMapping("/api/estado-cuenta")
public class EstadoCuentaController {

    private final EstadoCuentaService estadoCuentaService;

    public EstadoCuentaController(EstadoCuentaService estadoCuentaService) {
        this.estadoCuentaService = estadoCuentaService;
    }

    /**
     * Busca contribuyentes cuyo codigo, nombre o documento contengan el texto indicado.
     *
     * <p>Ejemplo: GET /api/estado-cuenta/contribuyentes?q=MEDINA</p>
     */
    @GetMapping("/contribuyentes")
    public List<ContribuyenteCuentaRespuesta> buscarContribuyentes(
            @RequestParam @NotBlank(message = "es obligatorio") String q) {
        return estadoCuentaService.buscarContribuyentes(q);
    }

    /**
     * Devuelve el estado de cuenta de un contribuyente, desglosado por tributo y periodo.
     *
     * <p>Ejemplo: GET /api/estado-cuenta/021390</p>
     */
    @GetMapping("/{codigo}")
    public EstadoCuentaRespuesta obtenerEstadoCuenta(
            @PathVariable @NotBlank(message = "es obligatorio") String codigo) {
        return estadoCuentaService.obtenerEstadoCuenta(codigo);
    }
}
