package pe.municipalidad.tributario.indicador;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/* Cuerpo de POST /api/metas. */
public record MetaIndicadorSolicitud(
        @NotBlank(message = "es obligatorio")
        String indicadorCodigo,

        @NotNull(message = "es obligatorio")
        @Min(value = 2000, message = "debe ser 2000 o posterior")
        @Max(value = 2100, message = "debe ser 2100 o anterior")
        Integer anio,

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe ser 1 o mayor")
        Integer numeroPeriodo,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 12, fraction = 2, message = "admite hasta 12 enteros y 2 decimales")
        BigDecimal valorMeta,

        @Size(max = 250, message = "admite hasta 250 caracteres")
        String observacion,

        /* Opcional: si no se envía, se usa el parámetro UMBRAL_DESVIO (10 % por defecto). */
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede superar 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 3 enteros y 2 decimales")
        BigDecimal umbralDesvio) {
}
