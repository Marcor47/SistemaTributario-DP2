package pe.municipalidad.tributario.indicador;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/*
 * Cuerpo de PUT /api/metas/{id}. Indicador, año y periodo no se modifican:
 * si están mal, la meta corresponde a otro periodo y se registra una nueva.
 */
public record MetaIndicadorActualizacion(
        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 12, fraction = 2, message = "admite hasta 12 enteros y 2 decimales")
        BigDecimal valorMeta,

        @Size(max = 250, message = "admite hasta 250 caracteres")
        String observacion,

        /* Opcional: si no se envía, la meta conserva su umbral de desvío actual. */
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede superar 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 3 enteros y 2 decimales")
        BigDecimal umbralDesvio) {
}
