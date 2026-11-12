package pe.municipalidad.tributario.ia;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU030 y HU031: cálculo de la segmentación con K-means (queda en borrador) y revisión, aprobación y publicación de
 * los segmentos de contribuyentes.
 */
@RestController
@RequestMapping("/api/segmentos")
public class SegmentoController {

    private final SegmentacionService segmentacionService;
    private final SegmentacionModeloService modelo;
    private final SegmentacionMapaService mapa;

    public SegmentoController(SegmentacionService segmentacionService, SegmentacionModeloService modelo,
            SegmentacionMapaService mapa) {
        this.segmentacionService = segmentacionService;
        this.modelo = modelo;
        this.mapa = mapa;
    }

    /* Mapa de la segmentación: puntos sin datos personales; sin idModelo, la publicada o el último borrador. */
    @GetMapping("/mapa")
    public SegmentacionMapaService.MapaRespuesta mapa(@RequestParam(required = false) Long idModelo,
            Authentication usuario) {
        return mapa.mapa(idModelo, puedeVerNombres(usuario));
    }

    /* Ficha de un contribuyente del mapa; el nombre solo con CONTRIBUYENTES:CONSULTAR, y queda auditado. */
    @GetMapping("/mapa/asignaciones/{idAsignacion}")
    public SegmentacionMapaService.FichaRespuesta ficha(@PathVariable Long idAsignacion, Authentication usuario) {
        return mapa.ficha(idAsignacion, puedeVerNombres(usuario), usuario.getName());
    }

    @GetMapping("/mapa/busqueda")
    public List<SegmentacionMapaService.ResultadoBusqueda> buscar(@RequestParam String q,
            @RequestParam(required = false) Long idModelo, Authentication usuario) {
        return mapa.buscar(idModelo, q, puedeVerNombres(usuario), usuario.getName());
    }

    private static boolean puedeVerNombres(Authentication usuario) {
        return usuario.getAuthorities().stream()
                .anyMatch(a -> SegmentacionMapaService.PERMISO_REIDENTIFICAR.equals(a.getAuthority()));
    }

    /* 201 con los segmentos en borrador; 200 si esa carga ya tenía el mismo modelo. */
    @PostMapping("/calculos")
    public ResponseEntity<CalculoSegmentacionRespuesta> calcular(Principal responsable) {
        CalculoSegmentacionRespuesta resultado = modelo.calcular(responsable.getName());
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado);
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
