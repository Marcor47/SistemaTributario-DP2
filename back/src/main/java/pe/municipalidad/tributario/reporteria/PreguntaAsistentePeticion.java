package pe.municipalidad.tributario.reporteria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;

/* Pregunta y filtros de la pantalla: se aplican cuando la pregunta no indica otro periodo, zona, tributo o segmento. */
public record PreguntaAsistentePeticion(
        @NotBlank(message = "es obligatoria") @Size(max = 500, message = "admite hasta 500 caracteres") String pregunta,
        @NotNull(message = "es obligatorio") YearMonth desde,
        @NotNull(message = "es obligatorio") YearMonth hasta,
        @Size(max = 20, message = "es demasiado largo") String zona,
        @Size(max = 25, message = "es demasiado largo") String tributo,
        @Size(max = 80, message = "es demasiado largo") String segmento) {
}
