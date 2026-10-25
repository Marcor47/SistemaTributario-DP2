package pe.municipalidad.tributario.analitica;

import java.math.BigDecimal;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Montos de las cuotas exigibles de un lote agrupados por tributo y zona: lo emitido y lo que sigue vencido sin
 * pagar. Es la base del índice de morosidad (RF-009).
 */
public record MontosMorosidad(Tributo tributo, String zona, BigDecimal montoEmitido,
        BigDecimal montoVencidoNoPagado) {
}
