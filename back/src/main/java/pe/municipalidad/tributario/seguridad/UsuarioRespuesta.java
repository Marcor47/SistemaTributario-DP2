package pe.municipalidad.tributario.seguridad;

import java.time.LocalDateTime;

public record UsuarioRespuesta(Long idUsuario, String nombreUsuario, String nombreCompleto, String correo,
        NombreRol rol, boolean activo, LocalDateTime fechaCreacion) {
}
