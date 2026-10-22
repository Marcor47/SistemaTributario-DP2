package pe.municipalidad.tributario.indicador;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Catálogo de indicadores para que el front arme el selector de la pantalla de metas (HU009). */
@RestController
@RequestMapping("/api/indicadores")
public class IndicadorController {

    private final MetaIndicadorService metaIndicadorService;

    public IndicadorController(MetaIndicadorService metaIndicadorService) {
        this.metaIndicadorService = metaIndicadorService;
    }

    @GetMapping
    public List<IndicadorRespuesta> listarActivos() {
        return metaIndicadorService.listarIndicadoresActivos();
    }
}
