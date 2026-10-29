package pe.municipalidad.tributario.seguridad;

import java.util.Set;

public record SesionInternaRespuesta(Long idUsuario, String nombreUsuario, String nombreCompleto,
        NombreRol rol, Set<String> permisos, boolean cambioClavePendiente) {
}
