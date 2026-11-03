package pe.municipalidad.tributario.reclamo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record RegistrarReclamoSolicitud(
        @NotNull TipoReclamo tipoReclamo,
        @Size(max = 150) String referencia,
        @NotNull LocalDateTime fechaHoraIncidente,
        @NotBlank @Size(max = Reclamo.MAXIMO_CARACTERES_COMENTARIO) String comentario) {
}
