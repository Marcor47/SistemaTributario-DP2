package pe.municipalidad.tributario.regularidad;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU010 - API de Regularidad de Pago del Contribuyente.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/contribuyentes/{codigo}/regularidad?anio=2025  calculo de regularidad del contribuyente en un anio.</li>
 *   <li>GET /api/regularidad/{codigo}?anio=2025               alias directo para facilitar integracion con el front.</li>
 * </ul>
 *
 * <p>El CORS para http://localhost:5173 ya esta configurado en CorsConfig.</p>
 */
@RestController
public class RegularidadPagoController {

    private final RegularidadPagoService regularidadPagoService;

    public RegularidadPagoController(RegularidadPagoService regularidadPagoService) {
        this.regularidadPagoService = regularidadPagoService;
    }

    /**
     * Devuelve el porcentaje de cuotas pagadas a tiempo por el contribuyente en el anio especificado
     * (o en el anio actual si se omite el parametro).
     *
     * <p>Ejemplos:</p>
     * <ul>
     *   <li>GET /api/contribuyentes/014502/regularidad?anio=2025</li>
     *   <li>GET /api/contribuyentes/021390/regularidad</li>
     * </ul>
     */
    @GetMapping({"/api/contribuyentes/{codigo}/regularidad", "/api/regularidad/{codigo}"})
    public RegularidadPagoRespuesta obtenerRegularidad(
            @PathVariable @NotBlank(message = "es obligatorio") String codigo,
            @RequestParam(required = false) Integer anio) {
        return regularidadPagoService.calcularRegularidad(codigo, anio);
    }
}
