package pe.municipalidad.tributario.documentos;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/*
 * HU012: Solicitud para procesar y enviar alertas de mora temprana a partir de un lote del ETL.
 * Si registros es nulo, se extraen desde SAMNET para el lote indicado.
 */
public record ProcesarAlertasMoraSolicitud(
        @NotNull(message = "El id del lote es obligatorio")
        Long idLote,
        List<Map<String, Object>> registros) {
}

