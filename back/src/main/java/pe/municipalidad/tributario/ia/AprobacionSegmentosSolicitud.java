package pe.municipalidad.tributario.ia;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/* Cuerpo de POST /api/segmentos/aprobaciones: los segmentos de la segmentación que aprueba la jefatura. */
public record AprobacionSegmentosSolicitud(
        @NotEmpty(message = "debe incluir al menos un segmento")
        @Size(max = 50, message = "admite hasta 50 segmentos")
        List<@NotNull(message = "no admite ids nulos") Long> idsSegmento) {
}
