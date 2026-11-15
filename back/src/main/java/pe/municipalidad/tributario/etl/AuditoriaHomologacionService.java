package pe.municipalidad.tributario.etl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;

/*
 * Servicio de solo lectura para la auditoría de decisiones de homologación (HU040 / RF-053).
 * Permite consultar el detalle de cómo se resolvió la homologación para cualquier registro maestro.
 * No expone métodos de modificación: la auditoría es estrictamente inalterable.
 */
@Service
@Transactional(readOnly = true)
public class AuditoriaHomologacionService {

    private final DecisionHomologacionRepository decisionRepository;
    private final EntidadMaestraRepository entidadMaestraRepository;
    private final CorrespondenciaOrigenRepository correspondencias;

    public AuditoriaHomologacionService(
            DecisionHomologacionRepository decisionRepository,
            EntidadMaestraRepository entidadMaestraRepository,
            CorrespondenciaOrigenRepository correspondencias) {
        this.decisionRepository = decisionRepository;
        this.entidadMaestraRepository = entidadMaestraRepository;
        this.correspondencias = correspondencias;
    }

    public List<DecisionHomologacionRespuesta> listarPorMaestro(Long idMaestro) {
        if (!entidadMaestraRepository.existsById(idMaestro)) {
            throw new RecursoNoEncontradoException("ENTIDAD_MAESTRA_NO_ENCONTRADA",
                    "No existe la entidad maestra con id " + idMaestro + ".");
        }
        return decisionRepository.findByEntidadMaestraIdMaestroOrderByFechaHoraAsc(idMaestro).stream()
                .map(DecisionHomologacionRespuesta::desde)
                .toList();
    }

    public List<DecisionHomologacionRespuesta> listarPorSeudonimo(String seudonimo) {
        EntidadMaestra maestro = entidadMaestraRepository.findBySeudonimo(seudonimo)
                .orElseThrow(() -> new RecursoNoEncontradoException("ENTIDAD_MAESTRA_NO_ENCONTRADA",
                        "No existe la entidad maestra con seudónimo " + seudonimo + "."));
        return decisionRepository.findByEntidadMaestraIdMaestroOrderByFechaHoraAsc(maestro.getIdMaestro()).stream()
                .map(DecisionHomologacionRespuesta::desde)
                .toList();
    }

    /* HU040: decisiones del registro maestro al que quedó asociado un código de SAMNET. */
    public List<DecisionHomologacionRespuesta> listarPorCodigo(TipoEntidad tipoEntidad, String codigo) {
        List<CorrespondenciaOrigen> vinculos = correspondencias.buscarPorClave(codigo, tipoEntidad, "SAMNET");
        if (vinculos.isEmpty()) {
            throw new RecursoNoEncontradoException("CODIGO_SIN_HOMOLOGAR",
                    "El código " + codigo + " no está vinculado a ningún registro maestro.");
        }
        return vinculos.stream()
                .map(v -> v.getEntidadMaestra().getIdMaestro())
                .distinct()
                .flatMap(id -> decisionRepository.findByEntidadMaestraIdMaestroOrderByFechaHoraAsc(id).stream())
                .map(DecisionHomologacionRespuesta::desde)
                .toList();
    }

    public DecisionHomologacionRespuesta obtenerDecision(Long idDecision) {
        DecisionHomologacion decision = decisionRepository.findById(idDecision)
                .orElseThrow(() -> new RecursoNoEncontradoException("DECISION_NO_ENCONTRADA",
                        "No existe la decisión de homologación con id " + idDecision + "."));
        return DecisionHomologacionRespuesta.desde(decision);
    }
}

