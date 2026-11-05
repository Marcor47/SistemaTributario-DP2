package pe.municipalidad.tributario.obligacion;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * TIM mensual de los tributos municipales (Código Tributario, art. 33): la fija la Municipalidad por ordenanza y no
 * puede superar la de SUNAT (0,90 % mensual). Es configurable porque cambia por ordenanza.
 */
@Component
public class TasaInteresMoratorio {

    private static final BigDecimal TIM_MAXIMA_SUNAT = new BigDecimal("0.90");

    private final BigDecimal mensual;

    public TasaInteresMoratorio(@Value("${tributario.tributos.tim-mensual}") BigDecimal mensual) {
        if (mensual.signum() < 0 || mensual.compareTo(TIM_MAXIMA_SUNAT) > 0) {
            throw new IllegalArgumentException("La TIM debe estar entre 0 y " + TIM_MAXIMA_SUNAT + " % mensual.");
        }
        this.mensual = mensual;
    }

    public BigDecimal mensual() {
        return mensual;
    }
}
