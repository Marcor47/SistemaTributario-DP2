package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;

public enum UnidadMedida {

    PORCENTAJE(new BigDecimal("100")),
    SOLES(null),
    CANTIDAD(null);

    /* null significa que la unidad no tiene un tope natural. */
    private final BigDecimal valorMaximo;

    UnidadMedida(BigDecimal valorMaximo) {
        this.valorMaximo = valorMaximo;
    }

    public boolean admite(BigDecimal valor) {
        return valorMaximo == null || valor.compareTo(valorMaximo) <= 0;
    }

    public BigDecimal getValorMaximo() {
        return valorMaximo;
    }
}
