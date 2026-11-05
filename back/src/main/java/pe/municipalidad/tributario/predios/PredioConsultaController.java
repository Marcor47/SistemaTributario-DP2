package pe.municipalidad.tributario.predios;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* HU002: consulta de predios por código o por contribuyente, paginada. Requiere PREDIOS:CONSULTAR. */
@RestController
@RequestMapping("/api/predios")
public class PredioConsultaController {

    private final PredioConsultaService predioConsultaService;

    public PredioConsultaController(PredioConsultaService predioConsultaService) {
        this.predioConsultaService = predioConsultaService;
    }

    @GetMapping
    public PaginaPrediosRespuesta buscar(
            @RequestParam String consulta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativa") int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = PredioConsultaService.TAMANO_MAXIMO, message = "admite hasta 50") int tamano) {
        return predioConsultaService.buscar(consulta, pagina, tamano);
    }
}
