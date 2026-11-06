package pe.municipalidad.tributario.estadocuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;

/**
 * HU004 / HU014 / RF-018 - fila del estado de cuenta: una cuota de un tributo. El Impuesto Predial va por cuota
 * trimestral (periodo 2026-T3); los arbitrios por predio y mes (periodo 2026-07), con el detalle de sus servicios.
 * deuda es el insoluto; si la cuota está vencida corre el interés moratorio y totalAPagar los suma.
 */
public record EstadoCuentaFilaRespuesta(
        String id,
        String tributo,
        String tributoCodigo,
        int anio,
        int cuota,
        String periodo,
        String codigoPredio,
        BigDecimal emitido,
        BigDecimal pagado,
        BigDecimal deuda,
        BigDecimal interesMoratorio,
        BigDecimal totalAPagar,
        String estado,
        LocalDate fechaVencimiento,
        long diasVencida,
        BigDecimal descuento,
        BigDecimal porcentajeDescuento,
        String etapaCobranza,
        List<ServicioRespuesta> servicios) {

    public record ServicioRespuesta(String servicio, String descripcion, BigDecimal monto) {
    }

    public static EstadoCuentaFilaRespuesta desde(Obligacion o, LocalDate fechaCorte, BigDecimal timMensual) {
        EstadoObligacion estadoEnum = o.estadoAl(fechaCorte);
        String estadoTexto = switch (estadoEnum) {
            case PAGADA    -> "Pagada";
            case VENCIDA   -> "Vencida";
            case PENDIENTE -> "Pendiente";
        };
        String etapa = o.getEtapaCobranza() == null ? null : o.getEtapaCobranza().getNombre();
        BigDecimal interes = o.interesMoratorioAl(fechaCorte, timMensual);
        return new EstadoCuentaFilaRespuesta(
                String.valueOf(o.getIdObligacion()),
                o.getTributo().getDescripcion(),
                o.getTributo().name(),
                o.getAnio(),
                o.getCuota(),
                o.etiquetaPeriodo(),
                o.getCodigoPredio(),
                o.getMontoEmitido(),
                o.getMontoPagado(),
                o.saldo(),
                interes,
                o.saldo().add(interes),
                estadoTexto,
                o.getFechaVencimiento(),
                estadoEnum == EstadoObligacion.VENCIDA ? o.diasVencidaAl(fechaCorte) : 0,
                o.getMontoDescontado(),
                o.getPorcentajeDescuento(),
                etapa,
                o.getServicios().stream()
                        .map(s -> new ServicioRespuesta(s.servicio().name(), s.servicio().getDescripcion(), s.monto()))
                        .toList());
    }
}
