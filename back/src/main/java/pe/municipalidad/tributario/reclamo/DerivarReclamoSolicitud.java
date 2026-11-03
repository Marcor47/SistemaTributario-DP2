package pe.municipalidad.tributario.reclamo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DerivarReclamoSolicitud(
        @NotNull @Positive Long idArea,
        @NotNull @Positive Long idResponsable,
        @Size(max = 400) String observacion) {
}
