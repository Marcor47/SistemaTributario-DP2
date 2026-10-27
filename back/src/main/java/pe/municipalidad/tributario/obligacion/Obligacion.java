package pe.municipalidad.tributario.obligacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;

/*
 * Cuota de un tributo para un periodo (RF-004, RF-007, RF-014). Se obtiene de SAMNET con SamnetGateway.
 * El Impuesto Predial se emite al contribuyente; los arbitrios por predio, por eso codigoPredio es opcional.
 * El estado de pago no se guarda: depende de la fecha de corte (estadoAl).
 */
public class Obligacion {

    private final Long idObligacion;
    private final String codigoContribuyente;
    private final String codigoPredio;
    private final Tributo tributo;
    private final int anio;
    private final int cuota;
    private final LocalDate fechaVencimiento;
    private final BigDecimal montoEmitido;
    private final BigDecimal montoPagado;
    private final BigDecimal montoDescontado;
    private final BigDecimal porcentajeDescuento;
    private final EtapaCobranza etapaCobranza;
    private final LocalDate fechaEtapa;

    public Obligacion(Long idObligacion, String codigoContribuyente, String codigoPredio, Tributo tributo, int anio,
            int cuota, LocalDate fechaVencimiento, BigDecimal montoEmitido, BigDecimal montoPagado,
            BigDecimal porcentajeDescuento, EtapaCobranza etapaCobranza, LocalDate fechaEtapa) {
        this(idObligacion, codigoContribuyente, codigoPredio, tributo, anio, cuota, fechaVencimiento,
                montoEmitido, montoPagado, BigDecimal.ZERO, porcentajeDescuento, etapaCobranza, fechaEtapa);
    }

    public Obligacion(Long idObligacion, String codigoContribuyente, String codigoPredio, Tributo tributo, int anio,
            int cuota, LocalDate fechaVencimiento, BigDecimal montoEmitido, BigDecimal montoPagado,
            BigDecimal montoDescontado, BigDecimal porcentajeDescuento, EtapaCobranza etapaCobranza,
            LocalDate fechaEtapa) {
        this.idObligacion = idObligacion;
        this.codigoContribuyente = codigoContribuyente;
        this.codigoPredio = codigoPredio;
        this.tributo = tributo;
        this.anio = anio;
        this.cuota = cuota;
        this.fechaVencimiento = fechaVencimiento;
        this.montoEmitido = montoEmitido;
        this.montoPagado = montoPagado;
        this.montoDescontado = montoDescontado;
        this.porcentajeDescuento = porcentajeDescuento;
        this.etapaCobranza = etapaCobranza;
        this.fechaEtapa = fechaEtapa;
    }

    /* Insoluto pendiente: monto emitido menos lo pagado. */
    public BigDecimal saldo() {
        return montoEmitido.subtract(montoPagado).subtract(montoDescontado);
    }

    public EstadoObligacion estadoAl(LocalDate fecha) {
        if (saldo().signum() <= 0) {
            return EstadoObligacion.PAGADA;
        }
        return fechaVencimiento.isBefore(fecha) ? EstadoObligacion.VENCIDA : EstadoObligacion.PENDIENTE;
    }

    public boolean estaVencida(LocalDate fecha) {
        return estadoAl(fecha) == EstadoObligacion.VENCIDA;
    }

    /* Saldo con el descuento del beneficio aplicable; el porcentaje lo calcula SAMNET (RNF-004). */
    public BigDecimal montoConBeneficio() {
        BigDecimal descuento = saldo().multiply(porcentajeDescuento).divide(BigDecimal.valueOf(100), 2,
                RoundingMode.HALF_UP);
        return saldo().subtract(descuento);
    }

    /* Días transcurridos desde el vencimiento; 0 si aún no vence. */
    public long diasVencidaAl(LocalDate fecha) {
        return Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, fecha));
    }

    /* Mismo formato de periodo que usa el front: 2025-T3. */
    public String etiquetaPeriodo() {
        return anio + "-T" + cuota;
    }

    public Long getIdObligacion() {
        return idObligacion;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public String getCodigoPredio() {
        return codigoPredio;
    }

    public Tributo getTributo() {
        return tributo;
    }

    public int getAnio() {
        return anio;
    }

    public int getCuota() {
        return cuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getMontoEmitido() {
        return montoEmitido;
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public BigDecimal getMontoDescontado() {
        return montoDescontado;
    }

    public BigDecimal getPorcentajeDescuento() {
        return porcentajeDescuento;
    }

    public EtapaCobranza getEtapaCobranza() {
        return etapaCobranza;
    }

    public LocalDate getFechaEtapa() {
        return fechaEtapa;
    }
}
