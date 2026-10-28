package pe.municipalidad.tributario.etl;

/*
 * HU026: DTO que expone una regla de calidad de datos basada en ISO/IEC 25012.
 */
public record ReglaCalidadRespuesta(
        Long idRegla,
        String codigo,
        String descripcion,
        boolean activa) {

    public static ReglaCalidadRespuesta desde(ReglaCalidad regla) {
        return new ReglaCalidadRespuesta(
                regla.getIdRegla(),
                regla.getCodigo(),
                regla.getDescripcion(),
                regla.isActiva());
    }
}
