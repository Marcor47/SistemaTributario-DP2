package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/*
 * Índice de morosidad guardado de una carga publicada, para el dashboard (HU008, RF-012). Cada valor trae los
 * montos base de su cálculo; son null si la carga no tiene hechos de cobranza.
 */
public record IndiceMorosidadRespuesta(
        Long idLote,
        LocalDate fechaCorte,
        int anio,
        int numeroPeriodo,
        String periodo,
        ValorPorAlcance total,
        List<ValorPorAlcance> porTributo,
        List<ValorPorAlcance> porZona) {

    public record ValorPorAlcance(
            String valorAlcance,
            BigDecimal montoEmitido,
            BigDecimal montoVencidoNoPagado,
            BigDecimal indice) {
    }
}
