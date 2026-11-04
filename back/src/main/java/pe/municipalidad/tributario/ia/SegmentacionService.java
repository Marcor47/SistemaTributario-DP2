package pe.municipalidad.tributario.ia;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU031 (RF-043): la jefatura revisa los segmentos que generó el modelo (HU030) y, al aprobarlos, quedan
 * publicados como filtro del dashboard. Se aprueba la segmentación completa de un modelo, porque sus segmentos
 * reparten a los contribuyentes entre sí. Publicar no dispara ninguna acción tributaria sobre los contribuyentes.
 */
@Service
@Transactional(readOnly = true)
public class SegmentacionService {

    private static final Logger LOG = LoggerFactory.getLogger(SegmentacionService.class);

    private final SegmentoRepository segmentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock reloj;

    public SegmentacionService(SegmentoRepository segmentoRepository, UsuarioRepository usuarioRepository,
            Clock reloj) {
        this.segmentoRepository = segmentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reloj = reloj;
    }

    public List<SegmentoRespuesta> listar(EstadoSegmento estado) {
        List<Segmento> segmentos = estado == null
                ? segmentoRepository.buscarTodos()
                : segmentoRepository.buscarPorEstado(estado);
        return segmentos.stream().map(SegmentoRespuesta::desde).toList();
    }

    /* Los que el dashboard ofrece como filtro (HU011). */
    public List<SegmentoRespuesta> listarPublicados() {
        return listar(EstadoSegmento.PUBLICADO);
    }

    /*
     * Aprueba y publica la segmentación; la que estaba publicada se retira para que el filtro no mezcle dos
     * segmentaciones distintas.
     */
    @Transactional
    public AprobacionSegmentosRespuesta aprobar(List<Long> idsSegmento, String nombreUsuario) {
        Set<Long> ids = new HashSet<>(idsSegmento);
        if (ids.size() != idsSegmento.size()) {
            throw new DatoInvalidoException("SEGMENTOS_REPETIDOS", "La lista de segmentos tiene ids repetidos.");
        }
        List<Segmento> segmentos = segmentoRepository.buscarPorIds(ids);
        if (segmentos.size() != ids.size()) {
            Set<Long> encontrados = segmentos.stream().map(Segmento::getIdSegmento).collect(Collectors.toSet());
            ids.removeAll(encontrados);
            throw new RecursoNoEncontradoException("SEGMENTO_NO_ENCONTRADO",
                    "No existen los segmentos " + ids.stream().sorted().toList() + ".");
        }
        List<Long> noBorrador = segmentos.stream().filter(s -> s.getEstado() != EstadoSegmento.BORRADOR)
                .map(Segmento::getIdSegmento).sorted().toList();
        if (!noBorrador.isEmpty()) {
            throw new ReglaNegocioException("SEGMENTO_NO_BORRADOR",
                    "Solo se aprueban segmentos en borrador; ya fueron revisados: " + noBorrador + ".");
        }
        Set<Long> modelos = segmentos.stream().map(s -> s.getModelo().getIdModelo()).collect(Collectors.toSet());
        if (modelos.size() != 1) {
            throw new ReglaNegocioException("SEGMENTOS_DE_DISTINTO_MODELO",
                    "Los segmentos a aprobar deben provenir de una misma segmentación.");
        }
        Long idModelo = modelos.iterator().next();
        List<Long> faltantes = segmentoRepository.buscarPorEstado(EstadoSegmento.BORRADOR).stream()
                .filter(s -> s.getModelo().getIdModelo().equals(idModelo) && !ids.contains(s.getIdSegmento()))
                .map(Segmento::getIdSegmento).sorted().toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("SEGMENTACION_INCOMPLETA",
                    "La segmentación tiene más segmentos en borrador que deben aprobarse juntos: " + faltantes + ".");
        }

        Usuario jefatura = usuarioRepository.findByNombreUsuario(nombreUsuario).filter(Usuario::isActivo)
                .orElseThrow(() -> new NoAutenticadoException("USUARIO_NO_IDENTIFICADO",
                        "Inicie sesión con un usuario interno activo."));
        List<Segmento> anteriores = segmentoRepository.buscarPorEstado(EstadoSegmento.PUBLICADO);
        anteriores.forEach(Segmento::retirar);

        LocalDateTime ahora = LocalDateTime.now(reloj);
        segmentos.forEach(segmento -> {
            segmento.aprobar(jefatura, ahora);
            segmento.publicar();
        });
        segmentoRepository.saveAllAndFlush(segmentos);
        LOG.info("Segmentación del modelo {} publicada: {} segmentos; {} retirados", idModelo, segmentos.size(),
                anteriores.size());
        return new AprobacionSegmentosRespuesta(
                segmentos.stream().map(SegmentoRespuesta::desde)
                        .sorted((a, b) -> a.nombre().compareTo(b.nombre())).toList(),
                anteriores.stream().map(Segmento::getIdSegmento).sorted().toList());
    }
}
