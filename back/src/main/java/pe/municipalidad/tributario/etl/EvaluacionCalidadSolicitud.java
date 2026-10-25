package pe.municipalidad.tributario.etl;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

/*
 * HU026: solicitud para evaluar reglas de calidad sobre un lote del ETL.
 * Puede recibir registros explícitos en el payload o extraerlos automáticamente con SamnetGateway.
 */
public record EvaluacionCalidadSolicitud(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser positivo")
        Long idLote,
        List<Map<String, Object>> registros) {

    public EvaluacionCalidadSolicitud(Long idLote) {
        this(idLote, null);
    }
}
