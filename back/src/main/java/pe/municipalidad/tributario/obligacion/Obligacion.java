package pe.municipalidad.tributario.obligacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;

/*
 * Cuota de un tributo para un periodo (RF-004, RF-007, RF-014). Se obtiene de SAMNET con SamnetGateway.
 * El Impuesto Predial se emite al contribuyente en 4 cuotas trimestrales; los arbitrios por predio y por mes, con el
 * detalle de sus servicios. Por eso codigoPredio y servicios solo vienen en los arbitrios.
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
    private final List<MontoServicio> servicios;
    private final LocalDate fechaUltimoPago;

    /* La TIM es mensual y el Código Tributario la aplica por día con meses de 30 días. */
    private static final int DIAS_MES_TIM = 30;

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
        this(idObligacion, codigoContribuyente, codigoPredio, tributo, anio, cuota, fechaVencimiento, montoEmitido,
                montoPagado, montoDescontado, porcentajeDescuento, etapaCobranza, fechaEtapa, List.of(), null);
    }

    public Obligacion(Long idObligacion, String codigoContribuyente, String codigoPredio, Tributo tributo, int anio,
            int cuota, LocalDate fechaVencimiento, BigDecimal montoEmitido, BigDecimal montoPagado,
            BigDecimal montoDescontado, BigDecimal porcentajeDescuento, EtapaCobranza etapaCobranza,
            LocalDate fechaEtapa, List<MontoServicio> servicios, LocalDate fechaUltimoPago) {
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
        this.servicios = List.copyOf(servicios);
        this.fechaUltimoPago = fechaUltimoPago;
    }

    /*
     * HU010: cancelada sin pagos después de su vencimiento. Si no hay fecha de pago la canceló un beneficio, lo que
     * no es un atraso.
     */
    public boolean pagadaATiempo() {
        return saldo().signum() <= 0 && (fechaUltimoPago == null || !fechaUltimoPago.isAfter(fechaVencimiento));
    }

    public LocalDate getFechaUltimoPago() {
        return fechaUltimoPago;
    }

    /*
     * Interés moratorio (Código Tributario, art. 33): interés simple diario de TIM / 30 sobre el insoluto, desde el
     * día siguiente al vencimiento hasta la fecha indicada. Solo corre si la cuota está vencida.
     */
    public BigDecimal interesMoratorioAl(LocalDate fecha, BigDecimal timMensual) {
        if (!estaVencida(fecha)) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal tasaDiaria = timMensual.divide(BigDecimal.valueOf(DIAS_MES_TIM * 100), 10, RoundingMode.HALF_UP);
        return saldo().multiply(tasaDiaria).multiply(BigDecimal.valueOf(diasVencidaAl(fecha)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public List<MontoServicio> getServicios() {
        return servicios;
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

    /* El Impuesto Predial se identifica por su cuota trimestral (2026-T3) y los arbitrios por su mes (2026-07). */
    public String etiquetaPeriodo() {
        return tributo == Tributo.ARBITRIOS
                ? String.format("%d-%02d", anio, cuota)
                : anio + "-T" + cuota;
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
