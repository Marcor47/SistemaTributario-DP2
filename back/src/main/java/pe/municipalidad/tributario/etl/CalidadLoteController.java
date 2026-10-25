package pe.municipalidad.tributario.etl;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU026: aplicar reglas de calidad en la carga del ETL (RF-038, RF-041).
 * Valida cada registro antes de pasarlo al modelo analítico. Si un registro incumple una regla,
 * el lote se rechaza y no afecta los datos ya publicados.
 */
@RestController
@RequestMapping("/api/etl/calidad")
public class CalidadLoteController {

    private final CalidadLoteService calidadLoteService;

    public CalidadLoteController(CalidadLoteService calidadLoteService) {
        this.calidadLoteService = calidadLoteService;
    }

    @GetMapping("/reglas")
    public List<ReglaCalidadRespuesta> listarReglas() {
        return calidadLoteService.listarReglas();
    }

    @GetMapping("/lotes/{idLote}")
    public EvaluacionCalidadRespuesta obtenerPorLote(@PathVariable Long idLote) {
        return calidadLoteService.obtenerCalidadLote(idLote);
    }

    @PostMapping("/evaluaciones")
    public EvaluacionCalidadRespuesta evaluar(@Valid @RequestBody EvaluacionCalidadSolicitud solicitud) {
        return calidadLoteService.evaluarLote(solicitud);
    }
}
