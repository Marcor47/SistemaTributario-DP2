package pe.municipalidad.tributario.etl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* Aprobar o rechazar un caso: el motivo es obligatorio y queda en la decisión inalterable (RF-053). */
public record ResolverCasoPeticion(@NotBlank @Size(max = 400) String motivo) {
}
