package pe.municipalidad.tributario.seguridad;

import jakarta.validation.constraints.NotNull;

public record CambiarRolSolicitud(@NotNull NombreRol nuevoRol, String motivo) {
}
