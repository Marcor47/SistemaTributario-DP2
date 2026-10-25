package pe.municipalidad.tributario.indicador;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/* Cuerpo de las operaciones que el ETL invoca sobre un lote: calcular la morosidad y evaluar las metas. */
public record LoteSolicitud(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser positivo")
        Long idLote) {
}
