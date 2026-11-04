package pe.municipalidad.tributario.dashboard;

import java.math.BigDecimal;
import java.util.Objects;
import pe.municipalidad.tributario.obligacion.Tributo;

/* Montos de las cuotas exigibles agrupados por tributo y zona: lo emitido, lo pagado y lo vencido sin pagar. */
public record MontosDashboard(Tributo tributo, String zona, BigDecimal montoEmitido, BigDecimal montoRecaudado,
        BigDecimal montoVencidoNoPagado) {

    public MontosDashboard {
        montoEmitido = Objects.requireNonNullElse(montoEmitido, BigDecimal.ZERO);
        montoRecaudado = Objects.requireNonNullElse(montoRecaudado, BigDecimal.ZERO);
        montoVencidoNoPagado = Objects.requireNonNullElse(montoVencidoNoPagado, BigDecimal.ZERO);
    }
}
