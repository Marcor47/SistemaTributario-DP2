package pe.municipalidad.tributario.declaracionjurada;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predios")
public class DeclaracionJuradaController {

    private final DeclaracionJuradaService service;

    public DeclaracionJuradaController(DeclaracionJuradaService service) {
        this.service = service;
    }

    @GetMapping("/{codigoPredio}/declaracion-jurada")
    public DeclaracionJuradaRespuesta consultar(@PathVariable String codigoPredio) {
        return service.consultar(codigoPredio);
    }
}
