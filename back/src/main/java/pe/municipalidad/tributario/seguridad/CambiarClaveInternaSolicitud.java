package pe.municipalidad.tributario.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarClaveInternaSolicitud(@NotBlank String claveActual,
        @NotBlank @Size(min = 12, max = 64) String claveNueva) {
}
