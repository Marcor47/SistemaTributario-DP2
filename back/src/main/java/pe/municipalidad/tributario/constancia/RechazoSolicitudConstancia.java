package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* Cuerpo de POST /api/gestion/constancias/{id}/rechazo. */
public record RechazoSolicitudConstancia(
        @NotBlank(message = "es obligatorio")
        @Size(max = 400, message = "admite hasta 400 caracteres")
        String motivo) {
}
