package pe.municipalidad.tributario.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearUsuarioSolicitud(@NotBlank String nombreUsuario, @NotBlank String nombreCompleto,
        @NotBlank @Email String correo, @NotBlank String claveInicial, @NotNull NombreRol rol) {
}
