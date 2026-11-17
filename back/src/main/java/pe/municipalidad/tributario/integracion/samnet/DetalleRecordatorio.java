package pe.municipalidad.tributario.integracion.samnet;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
 * HU016: datos de la obligación que acompañan al detalle de los recordatorios enviados. Se consultan a SAMNET
 * en bloque para no repetir una llamada por fila de la tabla paginada.
 */
public record DetalleRecordatorio(
        String idObligacion,
        String codigoContribuyente,
        String nombre,
        String tributo,
        LocalDate fechaVencimiento,
        BigDecimal saldo) {
}
