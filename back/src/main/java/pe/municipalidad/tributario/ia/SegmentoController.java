package pe.municipalidad.tributario.ia;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* HU031: revisión, aprobación y publicación de los segmentos de contribuyentes. */
@RestController
@RequestMapping("/api/segmentos")
public class SegmentoController {

    private final SegmentacionService segmentacionService;

    public SegmentoController(SegmentacionService segmentacionService) {
        this.segmentacionService = segmentacionService;
    }

    @GetMapping
    public List<SegmentoRespuesta> listar(@RequestParam(required = false) EstadoSegmento estado) {
        return segmentacionService.listar(estado);
    }

    @GetMapping("/publicados")
    public List<SegmentoRespuesta> listarPublicados() {
        return segmentacionService.listarPublicados();
    }

    @PostMapping("/aprobaciones")
    public AprobacionSegmentosRespuesta aprobar(@Valid @RequestBody AprobacionSegmentosSolicitud solicitud,
            Principal jefatura) {
        return segmentacionService.aprobar(solicitud.idsSegmento(), jefatura.getName());
    }
}
