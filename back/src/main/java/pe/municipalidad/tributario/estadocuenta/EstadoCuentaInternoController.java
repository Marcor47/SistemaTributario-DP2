package pe.municipalidad.tributario.estadocuenta;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU004: el Analista de Rentas consulta el estado de cuenta de cualquier contribuyente (permiso CUENTAS:CONSULTAR).
 * La ruta /api/estado-cuenta/{codigo} queda para el propio contribuyente.
 */
@RestController
public class EstadoCuentaInternoController {

    private final EstadoCuentaService estadoCuentaService;

    public EstadoCuentaInternoController(EstadoCuentaService estadoCuentaService) {
        this.estadoCuentaService = estadoCuentaService;
    }

    @GetMapping("/api/contribuyentes/{codigo}/estado-cuenta")
    public EstadoCuentaRespuesta obtener(@PathVariable @NotBlank(message = "es obligatorio") String codigo) {
        return estadoCuentaService.obtenerEstadoCuenta(codigo);
    }
}
