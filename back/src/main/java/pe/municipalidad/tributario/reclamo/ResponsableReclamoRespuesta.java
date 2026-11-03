package pe.municipalidad.tributario.reclamo;

import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.Usuario;

public record ResponsableReclamoRespuesta(Long idUsuario, String nombreUsuario, String nombreCompleto,
        NombreRol rol) {
    public static ResponsableReclamoRespuesta desde(Usuario usuario) {
        NombreRol rol = usuario.getRoles().stream().map(Rol::getNombre).findFirst().orElse(null);
        return new ResponsableReclamoRespuesta(usuario.getIdUsuario(), usuario.getNombreUsuario(),
                usuario.getNombreCompleto(), rol);
    }
}
