package pe.municipalidad.tributario.etl;

import java.time.LocalDateTime;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Un acceso a datos personales en claro (acción REIDENTIFICAR de seguridad.registro_auditoria): quién vio qué y cuándo.
 * entidad: CasoHomologacion (detalle de la bandeja), EntidadMaestra (reidentificación de seudónimos) o
 * AsignacionSegmento (búsqueda en el mapa de segmentos). detalle: el caso, seudónimo o búsqueda consultados.
 */
public record AccesoDatoRespuesta(
        Long idRegistro,
        LocalDateTime fechaHora,
        String nombreUsuario,
        String nombreCompletoUsuario,
        String entidad,
        String detalle) {

    static AccesoDatoRespuesta desde(RegistroAuditoria registro) {
        Usuario usuario = registro.getUsuario();
        return new AccesoDatoRespuesta(
                registro.getIdRegistro(),
                registro.getFechaHora(),
                usuario == null ? null : usuario.getNombreUsuario(),
                usuario == null ? null : usuario.getNombreCompleto(),
                registro.getEntidad(),
                registro.getValorNuevo());
    }
}
