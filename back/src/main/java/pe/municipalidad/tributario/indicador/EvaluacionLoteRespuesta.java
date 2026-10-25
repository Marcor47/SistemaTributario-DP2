package pe.municipalidad.tributario.indicador;

import java.time.LocalDate;
import java.util.List;

/*
 * Resultado de evaluar las metas con los valores de un lote: cuántas alertas aparecieron, cuántas
 * desaparecieron porque el indicador volvió dentro del umbral y cuáles quedaron activas.
 */
public record EvaluacionLoteRespuesta(
        Long idLote,
        LocalDate fechaCorte,
        int valoresEvaluados,
        int valoresSinMeta,
        int alertasGeneradas,
        int alertasResueltas,
        List<AlertaDesvioRespuesta> alertasActivas) {
}
