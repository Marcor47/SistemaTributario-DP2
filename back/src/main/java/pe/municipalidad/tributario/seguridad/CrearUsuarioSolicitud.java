package pe.municipalidad.tributario.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioSolicitud(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+") String nombreUsuario,
        @NotBlank @Size(max = 200) String nombreCompleto,
        @NotBlank @Email @Size(max = 120) String correo,
        @NotNull NombreRol rol) {
}
