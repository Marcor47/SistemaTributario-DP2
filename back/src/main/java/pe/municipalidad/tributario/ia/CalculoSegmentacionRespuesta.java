package pe.municipalidad.tributario.ia;

import java.time.LocalDate;
import java.util.List;

/*
 * Resultado de una corrida de la segmentación (HU030). nuevo = false cuando la misma carga con los mismos parámetros
 * ya tenía este modelo: no se crea otro y se devuelven sus segmentos.
 */
public record CalculoSegmentacionRespuesta(
        Long idModelo,
        String versionModelo,
        Long idLote,
        LocalDate fechaCorte,
        int contribuyentes,
        int k,
        double silueta,
        List<EvaluacionK> evaluacion,
        List<SegmentoRespuesta> segmentos,
        boolean nuevo) {

    /* valido: todos sus segmentos alcanzan el tamaño mínimo; de los válidos se elige el de mejor silueta. */
    public record EvaluacionK(int k, double silueta, double inercia, int tamanoMinimo, boolean valido) {
    }
}
