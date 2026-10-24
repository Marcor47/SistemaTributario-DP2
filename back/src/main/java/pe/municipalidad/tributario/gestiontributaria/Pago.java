package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;

/* Pago en ventanilla o en línea registrado en SAMNET, con lo que correspondía pagar (RF-062). */
public record Pago(
        String idPago,
        String codigoContribuyente,
        LocalDate fechaPago,
        CanalPago canal,
        BigDecimal montoPagado,
        BigDecimal montoCorrespondiente,
        String beneficioAplicado) {

    public BigDecimal diferencia() {
        return montoPagado.subtract(montoCorrespondiente);
    }
}
