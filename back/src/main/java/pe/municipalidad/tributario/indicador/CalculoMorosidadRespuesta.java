package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/*
 * Resultado de calcular el índice de morosidad de un lote (HU008): el índice total, por tributo y por zona, con
 * los montos usados, para poder contrastarlo con una muestra de control calculada a mano.
 */
public record CalculoMorosidadRespuesta(
        Long idLote,
        LocalDate fechaCorte,
        int anio,
        int numeroPeriodo,
        String periodo,
        /* Año de las cuotas medidas: el de corte o, si aún no vence ninguna, el anterior. */
        int anioCuotas,
        DetalleMorosidad total,
        List<DetalleMorosidad> porTributo,
        List<DetalleMorosidad> porZona,
        EvaluacionLoteRespuesta evaluacionAlertas) {

    public record DetalleMorosidad(
            AlcanceIndicador alcance,
            String valorAlcance,
            BigDecimal montoEmitido,
            BigDecimal montoVencidoNoPagado,
            BigDecimal indice) {
    }
}
