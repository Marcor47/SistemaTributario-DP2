package pe.municipalidad.tributario.etl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
 * HU026: resultado de la evaluación de calidad de un lote del ETL.
 * Informa el estado final (PUBLICADO o RECHAZADO), volúmenes y el detalle de cada regla ISO/IEC 25012.
 */
public record EvaluacionCalidadRespuesta(
        Long idLote,
        String fuente,
        LocalDate fechaCorte,
        EstadoLote estado,
        TipoEjecucion tipoEjecucion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        int registrosLeidos,
        int registrosAceptados,
        int registrosRechazados,
        boolean lotePublicado,
        List<ResultadoReglaRespuesta> resultadosReglas) {

    public static EvaluacionCalidadRespuesta desde(LoteETL lote) {
        return new EvaluacionCalidadRespuesta(
                lote.getIdLote(),
                lote.getFuente(),
                lote.getFechaCorte(),
                lote.getEstado(),
                lote.getTipoEjecucion(),
                lote.getFechaInicio(),
                lote.getFechaFin(),
                lote.getRegistrosLeidos(),
                lote.getRegistrosAceptados(),
                lote.getRegistrosRechazados(),
                lote.getEstado() == EstadoLote.PUBLICADO,
                lote.getResultados().stream().map(ResultadoReglaRespuesta::desde).toList());
    }
}
