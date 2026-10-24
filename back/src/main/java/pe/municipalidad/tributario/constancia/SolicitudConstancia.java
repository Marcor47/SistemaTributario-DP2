package pe.municipalidad.tributario.constancia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Trámite de la Constancia de No Adeudo desde la app o en ventanilla (RF-034, RF-055). La gestiona un operador:
 * el contribuyente firma y envía la solicitud; el operador verifica la deuda en SAMNET y emite la orden de pago;
 * tras el pago, el operador emite la constancia, que se firma automáticamente por la Municipalidad y va al buzón.
 * El servidor controla la secuencia y cada paso exige el anterior (RNF-029). En ventanilla no hay firma del
 * contribuyente y el trabajador actúa como operador. SolicitudConstancia * de 1 Contribuyente.
 */
@Entity
@Table(name = "solicitud_constancia", schema = "operativo")
public class SolicitudConstancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalSolicitud canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConstancia estado;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "documento_contribuyente", nullable = false, length = 12)
    private String documentoContribuyente;

    @Column(name = "motivo_rechazo", length = 400)
    private String motivoRechazo;

    /* Trabajador de ventanilla, si el canal es VENTANILLA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador")
    private Usuario trabajador;

    /* Operador que revisa la solicitud: verifica la deuda, emite la orden de pago y la constancia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operador")
    private Usuario operador;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    /* SolicitudConstancia 1 ◆ 0..2 FirmaDigital */
    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FirmaDigital> firmas = new ArrayList<>();

    protected SolicitudConstancia() {
    }

    public SolicitudConstancia(CanalSolicitud canal, String documentoContribuyente, Usuario trabajador,
            LocalDateTime fechaSolicitud) {
        if (canal == CanalSolicitud.VENTANILLA && trabajador == null) {
            throw new ReglaNegocioException("TRABAJADOR_REQUERIDO",
                    "La constancia en ventanilla requiere al trabajador que atiende.");
        }
        this.canal = canal;
        this.documentoContribuyente = documentoContribuyente;
        this.trabajador = trabajador;
        this.fechaSolicitud = fechaSolicitud;
        this.estado = EstadoConstancia.INICIADA;
    }

    /* RF-021: en la app, la solicitud no se envía al operador sin una firma válida del contribuyente. */
    public void registrarFirma(FirmaDigital firma) {
        exigirEstado(EstadoConstancia.INICIADA);
        if (canal != CanalSolicitud.APP) {
            throw new ReglaNegocioException("FIRMA_NO_APLICA", "En ventanilla no se firma la solicitud.");
        }
        if (!firma.isValida() || firma.getTipoFirmante() != TipoFirmante.CONTRIBUYENTE) {
            throw new ReglaNegocioException("FIRMA_INVALIDA",
                    "La firma digital del contribuyente no es válida.");
        }
        firma.asignarA(this);
        firmas.add(firma);
        estado = EstadoConstancia.FIRMADA;
    }

    /* El operador consulta la deuda en SAMNET; continúa solo si es 0, si no, rechaza la solicitud. */
    public boolean verificarDeuda(Usuario operador, BigDecimal deuda, LocalDateTime ahora) {
        exigirEstado(canal == CanalSolicitud.APP ? EstadoConstancia.FIRMADA : EstadoConstancia.INICIADA);
        asignarOperador(operador, ahora);
        if (deuda.signum() > 0) {
            rechazar(operador, "El contribuyente tiene deuda pendiente de S/ " + deuda + ".", ahora);
            return false;
        }
        estado = EstadoConstancia.DEUDA_VERIFICADA;
        return true;
    }

    /* La orden de pago del derecho de trámite le llega al contribuyente (RF-036). */
    public void emitirOrdenPago(TransaccionPago ordenPago) {
        exigirEstado(EstadoConstancia.DEUDA_VERIFICADA);
        if (ordenPago.getMotivo() != MotivoPago.DERECHO_TRAMITE || ordenPago.getSolicitudConstancia() != this) {
            throw new ReglaNegocioException("ORDEN_PAGO_INVALIDA",
                    "La orden de pago no corresponde al derecho de trámite de esta solicitud.");
        }
        estado = EstadoConstancia.ORDEN_PAGO_EMITIDA;
    }

    public void confirmarPago(TransaccionPago ordenPago) {
        exigirEstado(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        if (ordenPago.getEstado() != EstadoTransaccion.CONFIRMADA) {
            throw new ReglaNegocioException("PAGO_NO_CONFIRMADO", "El pago del derecho de trámite no está confirmado.");
        }
        estado = EstadoConstancia.PAGADA;
    }

    /* El operador emite la constancia; la firma de la Municipalidad se incorpora automáticamente (RF-037). */
    public void emitir(Usuario operador, FirmaDigital firmaMunicipalidad, LocalDateTime ahora) {
        exigirEstado(EstadoConstancia.PAGADA);
        if (!firmaMunicipalidad.isValida() || firmaMunicipalidad.getTipoFirmante() != TipoFirmante.MUNICIPALIDAD) {
            throw new ReglaNegocioException("FIRMA_INVALIDA", "La firma de la Municipalidad no es válida.");
        }
        asignarOperador(operador, ahora);
        firmaMunicipalidad.asignarA(this);
        firmas.add(firmaMunicipalidad);
        estado = EstadoConstancia.EMITIDA;
    }

    public void rechazar(Usuario operador, String motivo, LocalDateTime ahora) {
        if (estado == EstadoConstancia.EMITIDA || estado == EstadoConstancia.RECHAZADA) {
            throw new ReglaNegocioException("SOLICITUD_CERRADA", "La solicitud ya fue " + estado + ".");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("MOTIVO_REQUERIDO", "El rechazo requiere un motivo.");
        }
        asignarOperador(operador, ahora);
        this.motivoRechazo = motivo;
        this.estado = EstadoConstancia.RECHAZADA;
    }

    private void asignarOperador(Usuario operador, LocalDateTime ahora) {
        if (operador == null) {
            throw new ReglaNegocioException("OPERADOR_REQUERIDO", "Este paso lo realiza un operador.");
        }
        this.operador = operador;
        this.fechaRevision = ahora;
    }

    private void exigirEstado(EstadoConstancia esperado) {
        if (estado != esperado) {
            throw new ReglaNegocioException("SECUENCIA_INVALIDA",
                    "El trámite está en " + estado + " y este paso requiere " + esperado + ".");
        }
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public CanalSolicitud getCanal() {
        return canal;
    }

    public EstadoConstancia getEstado() {
        return estado;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public Usuario getTrabajador() {
        return trabajador;
    }

    public Usuario getOperador() {
        return operador;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public List<FirmaDigital> getFirmas() {
        return List.copyOf(firmas);
    }
}
