package pe.municipalidad.tributario.dashboard;

import java.math.BigDecimal;
import java.util.Objects;

public record MontosSegmento(Long idSegmento, String nombre, long contribuyentes, BigDecimal montoEmitido,
        BigDecimal montoRecaudado, BigDecimal montoVencidoNoPagado) {

    public MontosSegmento {
        montoEmitido = Objects.requireNonNullElse(montoEmitido, BigDecimal.ZERO);
        montoRecaudado = Objects.requireNonNullElse(montoRecaudado, BigDecimal.ZERO);
        montoVencidoNoPagado = Objects.requireNonNullElse(montoVencidoNoPagado, BigDecimal.ZERO);
    }
}
