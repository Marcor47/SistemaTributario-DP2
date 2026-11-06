package pe.municipalidad.tributario.reporteria;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/*
 * Agregados de la plataforma analítica para un periodo y unos filtros (RF-012). Las listas por mes empiezan en el mes
 * "desde"; las de zona, tributo, segmento, uso y canal siguen el orden del catálogo. Los importes están en soles.
 */
public record DatosReporteriaRespuesta(
        String desde,
        String hasta,
        Totales totales,
        Pricos pricos,
        Meta meta,
        Grupo porMes,
        ComparacionPorMes pricosPorMes,
        List<List<Double>> saldoPorSegmento,
        Grupo porZona,
        Grupo porTributo,
        Grupo porSegmento,
        List<Double> tributoEmitidoPricos,
        List<List<Double>> zonaTributoEmitido,
        List<List<Double>> zonaTributoDeuda,
        List<Double> antiguedad,
        List<Integer> canal,
        List<Integer> anticipacion,
        List<Double> usoEmitido,
        List<PuntoPrico> puntosPricos,
        Map<String, List<FilaContribuyente>> top,
        Destacado destacado) {

    public record Totales(double emitido, double cobrado, double recaudado, double deuda, int cuotas, int cuotasATiempo,
            int contribuyentes, int morosos, int buenosPagadores, double recaudadoBuenosPagadores) {
    }

    public record Pricos(int cantidad, int morosos, double emitido, double cobrado, double deuda) {
    }

    /* Meta del indicador de recaudación para el último mes del periodo y el alcance de los filtros. */
    public record Meta(BigDecimal valor, BigDecimal umbralDesvio, int anio, int numeroPeriodo) {
    }

    public record Grupo(List<Double> emitido, List<Double> cobrado, List<Double> recaudado, List<Double> deuda,
            List<Integer> cuotas, List<Integer> cuotasATiempo, List<Integer> contribuyentes, List<Integer> morosos,
            List<Integer> buenosPagadores) {
    }

    public record ComparacionPorMes(List<Double> emitidoPricos, List<Double> cobradoPricos, List<Double> emitidoResto,
            List<Double> cobradoResto) {
    }

    public record PuntoPrico(int id, String nombre, double emitido, double porcentajeCobrado, double deuda, int zona,
            int segmento, int uso) {
    }

    public record FilaContribuyente(int id, String codigo, String nombre, int zona, int segmento, int uso,
            boolean esPrico, boolean juridica, int predios, double emitido, double cobrado, double recaudado,
            double deuda, int cuotas, int cuotasATiempo, int cuotasVencidas, int atrasoMaximo, int tributoPrincipal,
            String ultimoPago) {
    }

    /* Contribuyente resaltado que puede quedar fuera de los 10 primeros, con su puesto en cada ranking. */
    public record Destacado(FilaContribuyente fila, Map<String, Integer> puestos) {
    }
}
