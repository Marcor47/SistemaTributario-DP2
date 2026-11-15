package pe.municipalidad.tributario.regularidad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/* HU021 (RF-035): clasificación del riesgo de mora de un contribuyente para orientar la cobranza preventiva. */
@RestController
public class RiesgoMoraController {

    private final RiesgoMoraService servicio;

    public RiesgoMoraController(RiesgoMoraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/contribuyentes/{codigo}/riesgo-mora")
    public RiesgoMoraService.RiesgoMoraRespuesta calcular(@PathVariable String codigo) {
        return servicio.calcular(codigo);
    }
}
