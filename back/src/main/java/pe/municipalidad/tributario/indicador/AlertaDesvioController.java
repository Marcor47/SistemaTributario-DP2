package pe.municipalidad.tributario.indicador;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU039: alerta de desvío de meta en el dashboard. El ETL llama a la evaluación al publicar cada lote;
 * el dashboard consulta las alertas activas.
 */
@RestController
@RequestMapping("/api/alertas-desvio")
public class AlertaDesvioController {

    private final AlertaDesvioService alertaDesvioService;

    public AlertaDesvioController(AlertaDesvioService alertaDesvioService) {
        this.alertaDesvioService = alertaDesvioService;
    }

    @GetMapping
    public List<AlertaDesvioRespuesta> listarActivas(@RequestParam(required = false) String indicadorCodigo) {
        return alertaDesvioService.listarActivas(indicadorCodigo);
    }

    @PostMapping("/evaluaciones")
    public EvaluacionLoteRespuesta evaluarLote(@Valid @RequestBody LoteSolicitud solicitud) {
        return alertaDesvioService.evaluarLote(solicitud.idLote());
    }
}
