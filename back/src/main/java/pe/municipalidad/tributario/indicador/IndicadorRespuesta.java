package pe.municipalidad.tributario.indicador;

public record IndicadorRespuesta(
        String codigo,
        String nombre,
        String definicion,
        String formula,
        String fuente,
        UnidadMedida unidadMedida,
        Periodicidad periodicidad,
        int periodosPorAnio,
        SentidoIndicador sentido) {

    public static IndicadorRespuesta desde(Indicador indicador) {
        return new IndicadorRespuesta(
                indicador.getCodigo(),
                indicador.getNombre(),
                indicador.getDefinicion(),
                indicador.getFormula(),
                indicador.getFuente(),
                indicador.getUnidadMedida(),
                indicador.getPeriodicidad(),
                indicador.getPeriodicidad().getPeriodosPorAnio(),
                indicador.getSentido());
    }
}
