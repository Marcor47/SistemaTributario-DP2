package pe.municipalidad.tributario.reclamo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderReclamoSolicitud(
        @NotBlank @Size(max = Reclamo.MAXIMO_CARACTERES_RESPUESTA) String respuesta) {
}
