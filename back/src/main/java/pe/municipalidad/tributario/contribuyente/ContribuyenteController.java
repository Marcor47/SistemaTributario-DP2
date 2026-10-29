package pe.municipalidad.tributario.contribuyente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* HU001: consultar contribuyente por documento. */
@RestController
@RequestMapping("/api/contribuyentes")
public class ContribuyenteController {

    private final ContribuyenteService contribuyenteService;

    public ContribuyenteController(ContribuyenteService contribuyenteService) {
        this.contribuyenteService = contribuyenteService;
    }

    @GetMapping
    public ContribuyenteRespuesta consultarPorDocumento(
            @RequestParam TipoDocumento tipoDocumento,
            @RequestParam @NotBlank(message = "es obligatorio") String numeroDocumento) {
        return contribuyenteService.consultarPorDocumento(tipoDocumento, numeroDocumento);
    }

    @GetMapping("/buscar")
    public List<ContribuyenteCoincidenciaRespuesta> buscar(
            @RequestParam @NotBlank(message = "es obligatorio")
            @Size(max = 120, message = "debe tener como máximo 120 caracteres") String q) {
        return contribuyenteService.buscar(q);
    }
}
