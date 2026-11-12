package pe.municipalidad.tributario.etl;

/*
 * Resultado de resolver un caso. Los maestros cambian en el acto; el modelo estrella refleja la decisión desde el
 * siguiente lote del ETL (los lotes publicados no se reescriben).
 */
public record ResolucionCasoRespuesta(
        Long idCaso,
        EstadoCaso estado,
        Long idDecision,
        Long idMaestroResultante,
        String seudonimoResultante,
        int correspondenciasMovidas,
        String efecto) {
}
