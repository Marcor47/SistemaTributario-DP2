package pe.municipalidad.tributario.reporteria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record GuardarVistaAnaliticaPeticion(
        @NotBlank(message = "es obligatorio") @Size(max = 60, message = "admite hasta 60 caracteres") String nombre,
        @NotNull(message = "es obligatorio") Map<String, Object> estado) {
}
