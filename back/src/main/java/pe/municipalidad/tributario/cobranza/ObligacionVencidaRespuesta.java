package pe.municipalidad.tributario.cobranza;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.obligacion.Tributo;

/* Fila del listado de obligaciones vencidas de un contribuyente, con la etapa en la que se encuentra cada una. */
public record ObligacionVencidaRespuesta(
        Long id,
        Tributo tributo,
        String tributoDescripcion,
        String periodo,
        String codigoPredio,
        LocalDate fechaVencimiento,
        BigDecimal saldo,
        long diasVencida,
        EtapaCobranza etapaActual,
        String etapaNombre) {
}
