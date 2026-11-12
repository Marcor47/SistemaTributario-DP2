package pe.municipalidad.tributario.etl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/* Unir el registro a otra entidad maestra que no es el candidato que propuso el ETL. */
public record ReasignarCasoPeticion(@NotNull Long idMaestro, @NotBlank @Size(max = 400) String motivo) {
}
