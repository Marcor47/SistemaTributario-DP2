package pe.municipalidad.tributario.etl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * DTO de respuesta para la auditoría de decisiones de homologación (RF-053 / HU040).
 * Contiene el nivel resolutor, puntaje, atributos coincidentes, tipo de decisión (automática o humana),
 * y los datos de auditoría humana cuando aplica (usuario, fecha y motivo).
 */
public record DecisionHomologacionRespuesta(
        Long idDecision,
        Long idMaestro,
        TipoEntidad tipoEntidad,
        String seudonimoMaestro,
        Long idCaso,
        NivelHomologacion nivelResolutor,
        BigDecimal puntaje,
        String atributosCoincidentes,
        TipoDecision tipoDecision,
        Long idUsuario,
        String nombreUsuario,
        String nombreCompletoUsuario,
        LocalDateTime fechaHora,
        String motivo) {

    public static DecisionHomologacionRespuesta desde(DecisionHomologacion decision) {
        EntidadMaestra maestro = decision.getEntidadMaestra();
        Usuario usuario = decision.getUsuario();
        CasoHomologacion caso = decision.getCaso();

        return new DecisionHomologacionRespuesta(
                decision.getIdDecision(),
                maestro != null ? maestro.getIdMaestro() : null,
                maestro != null ? maestro.getTipoEntidad() : null,
                maestro != null ? maestro.getSeudonimo() : null,
                caso != null ? caso.getIdCaso() : null,
                decision.getNivelResolutor(),
                decision.getPuntaje(),
                decision.getAtributosCoincidentes(),
                decision.getTipoDecision(),
                usuario != null ? usuario.getIdUsuario() : null,
                usuario != null ? usuario.getNombreUsuario() : null,
                usuario != null ? usuario.getNombreCompleto() : null,
                decision.getFechaHora(),
                decision.getMotivo());
    }
}

