package pe.municipalidad.tributario.estadocuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;

/**
 * HU014 / RF-018 - fila del desglose de estado de cuenta (un tributo x un periodo).
 * Incluye fecha de vencimiento, beneficios/descuentos y etapa de cobranza inductiva.
 */
public record EstadoCuentaFilaRespuesta(
        String id,
        String tributo,
        String periodo,
        BigDecimal emitido,
        BigDecimal pagado,
        BigDecimal deuda,
        String estado,
        LocalDate fechaVencimiento,
        BigDecimal descuento,
        BigDecimal porcentajeDescuento,
        String etapaCobranza) {

    public static EstadoCuentaFilaRespuesta desde(Obligacion o, LocalDate fechaCorte) {
        EstadoObligacion estadoEnum = o.estadoAl(fechaCorte);
        String estadoTexto = switch (estadoEnum) {
            case PAGADA    -> "Pagada";
            case VENCIDA   -> "Vencida";
            case PENDIENTE -> "Pendiente";
        };
        String etapa = o.getEtapaCobranza() == null ? null : o.getEtapaCobranza().getNombre();
        return new EstadoCuentaFilaRespuesta(
                String.valueOf(o.getIdObligacion()),
                o.getTributo().getDescripcion(),
                o.etiquetaPeriodo(),
                o.getMontoEmitido(),
                o.getMontoPagado(),
                o.saldo(),
                estadoTexto,
                o.getFechaVencimiento(),
                o.getMontoDescontado(),
                o.getPorcentajeDescuento(),
                etapa);
    }
}
