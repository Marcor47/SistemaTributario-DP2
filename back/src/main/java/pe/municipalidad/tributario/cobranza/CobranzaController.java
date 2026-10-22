package pe.municipalidad.tributario.cobranza;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* HU013: consultar la etapa de cobranza de una obligación vencida. */
@RestController
@RequestMapping("/api")
public class CobranzaController {

    private final CobranzaService cobranzaService;

    public CobranzaController(CobranzaService cobranzaService) {
        this.cobranzaService = cobranzaService;
    }

    /* Listado para elegir la obligación cuya etapa se quiere consultar. */
    @GetMapping("/contribuyentes/{codigo}/obligaciones-vencidas")
    public List<ObligacionVencidaRespuesta> listarVencidas(@PathVariable String codigo) {
        return cobranzaService.listarVencidas(codigo);
    }

    @GetMapping("/obligaciones/{id}/etapa-cobranza")
    public EtapaCobranzaRespuesta consultarEtapa(@PathVariable Long id) {
        return cobranzaService.consultarEtapa(id);
    }
}
