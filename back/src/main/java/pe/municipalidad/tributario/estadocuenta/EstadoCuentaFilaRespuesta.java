package pe.municipalidad.tributario.estadocuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;

/**
 * HU004 - fila del desglose de estado de cuenta (un tributo x un periodo).
 * La etiqueta del tributo usa la descripcion legible del enum (ej. "Impuesto Predial")
 * para que coincida con los literales que espera el front (TRIBUTOS en mocks/cuentas.js).
 */
public record EstadoCuentaFilaRespuesta(
        String id,
        String tributo,
        String periodo,
        BigDecimal emitido,
        BigDecimal pagado,
        BigDecimal deuda,
        String estado) {

    public static EstadoCuentaFilaRespuesta desde(Obligacion o, LocalDate fechaCorte) {
        EstadoObligacion estadoEnum = o.estadoAl(fechaCorte);
        String estadoTexto = switch (estadoEnum) {
            case PAGADA    -> "Pagada";
            case VENCIDA   -> "Vencida";
            case PENDIENTE -> "Pendiente";
        };
        return new EstadoCuentaFilaRespuesta(
                String.valueOf(o.getIdObligacion()),
                o.getTributo().getDescripcion(),
                o.etiquetaPeriodo(),
                o.getMontoEmitido(),
                o.getMontoPagado(),
                o.saldo(),
                estadoTexto);
    }
}
