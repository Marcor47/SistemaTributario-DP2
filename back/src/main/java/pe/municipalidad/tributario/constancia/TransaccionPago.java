package pe.municipalidad.tributario.constancia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import pe.municipalidad.tributario.common.ReglaNegocioException;

/*
 * Intento de pago en línea con la pasarela Niubiz; no almacena datos de tarjeta (RF-036, RNF-011).
 * TransaccionPago * paga * Obligacion (ids de SAMNET). SolicitudConstancia 1 cobra trámite 0..1 TransaccionPago.
 */
@Entity
@Table(name = "transaccion_pago", schema = "operativo")
public class TransaccionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaccion")
    private Long idTransaccion;

    @Column(name = "documento_contribuyente", nullable = false, length = 12)
    private String documentoContribuyente;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 20)
    private MotivoPago motivo;

    @Column(name = "monto_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "monto_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoDescuento;

    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoTransaccion estado;

    @Column(name = "id_orden_pasarela", unique = true, length = 40)
    private String idOrdenPasarela;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "comprobante", length = 60)
    private String comprobante;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud_constancia", unique = true)
    private SolicitudConstancia solicitudConstancia;

    @ElementCollection
    @CollectionTable(name = "transaccion_obligacion", schema = "operativo",
            joinColumns = @JoinColumn(name = "id_transaccion"))
    @Column(name = "id_obligacion", length = 20)
    private Set<String> obligaciones = new HashSet<>();

    protected TransaccionPago() {
    }

    private TransaccionPago(String documentoContribuyente, MotivoPago motivo, BigDecimal montoBase,
            BigDecimal montoDescuento, LocalDateTime fechaHora) {
        this.documentoContribuyente = documentoContribuyente;
        this.motivo = motivo;
        this.montoBase = montoBase;
        this.montoDescuento = montoDescuento;
        this.montoTotal = montoBase.subtract(montoDescuento);
        this.fechaHora = fechaHora;
        this.estado = EstadoTransaccion.INICIADA;
    }

    public static TransaccionPago deObligaciones(String documentoContribuyente, Set<String> idsObligacion,
            BigDecimal montoBase, BigDecimal montoDescuento, LocalDateTime fechaHora) {
        TransaccionPago transaccion = new TransaccionPago(documentoContribuyente, MotivoPago.OBLIGACIONES, montoBase,
                montoDescuento, fechaHora);
        transaccion.obligaciones.addAll(idsObligacion);
        return transaccion;
    }

    public static TransaccionPago deDerechoTramite(SolicitudConstancia solicitud, BigDecimal monto,
            LocalDateTime fechaHora) {
        TransaccionPago transaccion = new TransaccionPago(solicitud.getDocumentoContribuyente(),
                MotivoPago.DERECHO_TRAMITE, monto, BigDecimal.ZERO, fechaHora);
        transaccion.solicitudConstancia = solicitud;
        return transaccion;
    }

    /* Registra la orden creada en la pasarela. */
    public void iniciar(String idOrdenPasarela) {
        this.idOrdenPasarela = idOrdenPasarela;
    }

    /* Solo después de consultar a la pasarela servidor a servidor; nunca con el retorno del navegador (RNF-029). */
    public void confirmar(String comprobante) {
        if (estado != EstadoTransaccion.INICIADA) {
            throw new ReglaNegocioException("TRANSACCION_CERRADA", "La transacción ya fue " + estado + ".");
        }
        this.comprobante = comprobante;
        this.estado = EstadoTransaccion.CONFIRMADA;
    }

    public void rechazar() {
        if (estado == EstadoTransaccion.CONFIRMADA) {
            throw new ReglaNegocioException("TRANSACCION_CONFIRMADA", "La transacción ya fue confirmada.");
        }
        this.estado = EstadoTransaccion.RECHAZADA;
    }

    public void expirar() {
        if (estado == EstadoTransaccion.INICIADA) {
            this.estado = EstadoTransaccion.EXPIRADA;
        }
    }

    public Long getIdTransaccion() {
        return idTransaccion;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public MotivoPago getMotivo() {
        return motivo;
    }

    public BigDecimal getMontoBase() {
        return montoBase;
    }

    public BigDecimal getMontoDescuento() {
        return montoDescuento;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public EstadoTransaccion getEstado() {
        return estado;
    }

    public String getIdOrdenPasarela() {
        return idOrdenPasarela;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getComprobante() {
        return comprobante;
    }

    public SolicitudConstancia getSolicitudConstancia() {
        return solicitudConstancia;
    }

    public Set<String> getObligaciones() {
        return Set.copyOf(obligaciones);
    }
}
