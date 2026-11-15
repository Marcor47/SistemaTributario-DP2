package pe.municipalidad.tributario.reporte;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.ReidentificacionService;
import pe.municipalidad.tributario.etl.TipoEntidad;
import pe.municipalidad.tributario.ia.AsignacionSegmento;
import pe.municipalidad.tributario.ia.AsignacionSegmentoRepository;
import pe.municipalidad.tributario.ia.EstadoSegmento;
import pe.municipalidad.tributario.ia.SegmentoRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * HU045: resuelve el segmento de la última segmentación publicada para aplicar restricciones del catálogo.
 * El modelo analítico guarda seudónimos; la reidentificación se audita y solo se usa para filtrar metadatos.
 */
@Service
public class SegmentoAccesoService {

    private final ReidentificacionService reidentificacion;
    private final AsignacionSegmentoRepository asignaciones;
    private final SegmentoRepository segmentos;

    public SegmentoAccesoService(ReidentificacionService reidentificacion,
            AsignacionSegmentoRepository asignaciones, SegmentoRepository segmentos) {
        this.reidentificacion = reidentificacion;
        this.asignaciones = asignaciones;
        this.segmentos = segmentos;
    }

    @Transactional(readOnly = true)
    public Map<String, String> porCodigo(Collection<String> codigos, Usuario usuario) {
        if (codigos == null || codigos.isEmpty()) return Map.of();
        Map<String, String> seudonimos = reidentificacion.seudonimosDe(codigos, TipoEntidad.CONTRIBUYENTE, usuario);
        Map<String, String> porSeudonimo = porSeudonimo(seudonimos.values());
        Map<String, String> porCodigo = new LinkedHashMap<>();
        seudonimos.forEach((codigo, seudonimo) -> porCodigo.put(codigo, porSeudonimo.get(seudonimo)));
        return porCodigo;
    }

    @Transactional(readOnly = true)
    public Map<String, String> porSeudonimo(Collection<String> seudonimos) {
        if (seudonimos == null || seudonimos.isEmpty()) return Map.of();
        Map<String, String> resultado = new LinkedHashMap<>();
        for (AsignacionSegmento asignacion : asignaciones.publicadosPorSeudonimos(seudonimos,
                EstadoSegmento.PUBLICADO)) {
            resultado.putIfAbsent(asignacion.getContribuyente().getSeudonimoContribuyente(),
                    asignacion.getSegmento().getNombre());
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<String> publicados() {
        return segmentos.buscarPorEstado(EstadoSegmento.PUBLICADO).stream()
                .map(segmento -> segmento.getNombre())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
