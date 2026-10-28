package pe.municipalidad.tributario.etl;

/*
 * HU026: DTO con el resultado de evaluar una regla de calidad en un lote del ETL.
 */
public record ResultadoReglaRespuesta(
        String codigoRegla,
        String descripcionRegla,
        int registrosEvaluados,
        int registrosConError,
        ResultadoCalidad resultado,
        String detalle) {

    public static ResultadoReglaRespuesta desde(ResultadoRegla rr) {
        return new ResultadoReglaRespuesta(
                rr.getRegla().getCodigo(),
                rr.getRegla().getDescripcion(),
                rr.getRegistrosEvaluados(),
                rr.getRegistrosConError(),
                rr.getResultado(),
                rr.getDetalle());
    }
}
