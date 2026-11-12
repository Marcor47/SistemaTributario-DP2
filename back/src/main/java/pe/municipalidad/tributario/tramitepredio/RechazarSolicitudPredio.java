package pe.municipalidad.tributario.tramitepredio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* HU043: el motivo llega al buzón del contribuyente junto con la deuda existente del predio. */
public record RechazarSolicitudPredio(
        @NotBlank(message = "es obligatorio") @Size(max = 400, message = "admite hasta 400 caracteres")
        String motivo) {
}
