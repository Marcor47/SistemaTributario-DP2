package pe.municipalidad.tributario.seguridad;

import java.util.List;
import java.util.Set;

public record SesionInternaRespuesta(Long idUsuario, String nombreUsuario, String nombreCompleto,
        NombreRol rol, List<String> roles, Set<String> permisos, boolean cambioClavePendiente) {
}
