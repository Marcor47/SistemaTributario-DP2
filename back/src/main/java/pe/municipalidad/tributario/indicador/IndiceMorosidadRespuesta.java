package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/* Índice de morosidad guardado de una carga publicada, para el dashboard (HU008, RF-012). */
public record IndiceMorosidadRespuesta(
        Long idLote,
        LocalDate fechaCorte,
        int anio,
        int numeroPeriodo,
        String periodo,
        BigDecimal total,
        List<ValorPorAlcance> porTributo,
        List<ValorPorAlcance> porZona) {

    public record ValorPorAlcance(String valorAlcance, BigDecimal indice) {
    }
}
