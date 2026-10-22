package pe.municipalidad.tributario.indicador;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* HU009: configurar una meta por indicador. */
@RestController
@RequestMapping("/api/metas")
public class MetaIndicadorController {

    private final MetaIndicadorService metaIndicadorService;

    public MetaIndicadorController(MetaIndicadorService metaIndicadorService) {
        this.metaIndicadorService = metaIndicadorService;
    }

    @GetMapping
    public List<MetaIndicadorRespuesta> buscar(
            @RequestParam(required = false) String indicadorCodigo,
            @RequestParam(required = false) Integer anio) {
        return metaIndicadorService.buscarMetas(indicadorCodigo, anio);
    }

    @GetMapping("/{id}")
    public MetaIndicadorRespuesta obtener(@PathVariable Long id) {
        return metaIndicadorService.obtenerMeta(id);
    }

    @PostMapping
    public ResponseEntity<MetaIndicadorRespuesta> registrar(@Valid @RequestBody MetaIndicadorSolicitud solicitud) {
        MetaIndicadorRespuesta meta = metaIndicadorService.registrarMeta(solicitud);
        return ResponseEntity.created(URI.create("/api/metas/" + meta.id())).body(meta);
    }

    @PutMapping("/{id}")
    public MetaIndicadorRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody MetaIndicadorActualizacion actualizacion) {
        return metaIndicadorService.actualizarMeta(id, actualizacion);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        metaIndicadorService.eliminarMeta(id);
        return ResponseEntity.noContent().build();
    }
}
