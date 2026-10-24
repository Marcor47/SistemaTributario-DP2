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
 * Trámite de la Constancia de No Adeudo desde la app o en ventanilla (RF-034, RF-055). El servidor controla
 * la secuencia: deuda 0 → firma válida → pago verificado → emisión (RNF-029); cada paso exige el anterior.
 * SolicitudConstancia * de 1 Contribuyente (documentoContribuyente).
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

    /* Continúa solo si la deuda consultada en SAMNET es 0; si no, rechaza la solicitud. */
    public boolean verificarDeuda(BigDecimal deuda) {
        exigirEstado(EstadoConstancia.INICIADA);
        if (deuda.signum() > 0) {
            rechazar("El contribuyente tiene deuda pendiente de S/ " + deuda + ".");
            return false;
        }
        estado = EstadoConstancia.DEUDA_VERIFICADA;
        return true;
    }

    public void registrarFirma(FirmaDigital firma) {
        exigirEstado(EstadoConstancia.DEUDA_VERIFICADA);
        if (!firma.isValida() || firma.getTipoFirmante() != TipoFirmante.CONTRIBUYENTE) {
            throw new ReglaNegocioException("FIRMA_INVALIDA",
                    "La firma digital del contribuyente no es válida.");
        }
        firma.asignarA(this);
        firmas.add(firma);
        estado = EstadoConstancia.FIRMADA;
    }

    /* En ventanilla no hay firma digital del contribuyente: el trabajador lo atiende con su DNI. */
    public void omitirFirmaEnVentanilla() {
        exigirEstado(EstadoConstancia.DEUDA_VERIFICADA);
        if (canal != CanalSolicitud.VENTANILLA) {
            throw new ReglaNegocioException("FIRMA_REQUERIDA", "La solicitud desde la app requiere firma digital.");
        }
        estado = EstadoConstancia.FIRMADA;
    }

    public void confirmarPago(TransaccionPago transaccion) {
        exigirEstado(EstadoConstancia.FIRMADA);
        if (transaccion.getEstado() != EstadoTransaccion.CONFIRMADA) {
            throw new ReglaNegocioException("PAGO_NO_CONFIRMADO", "El pago del derecho de trámite no está confirmado.");
        }
        estado = EstadoConstancia.PAGADA;
    }

    /* La firma de la Municipalidad se agrega al emitir (RF-037). */
    public void emitir(FirmaDigital firmaMunicipalidad) {
        exigirEstado(EstadoConstancia.PAGADA);
        if (!firmaMunicipalidad.isValida() || firmaMunicipalidad.getTipoFirmante() != TipoFirmante.MUNICIPALIDAD) {
            throw new ReglaNegocioException("FIRMA_INVALIDA", "La firma de la Municipalidad no es válida.");
        }
        firmaMunicipalidad.asignarA(this);
        firmas.add(firmaMunicipalidad);
        estado = EstadoConstancia.EMITIDA;
    }

    public void rechazar(String motivo) {
        if (estado == EstadoConstancia.EMITIDA) {
            throw new ReglaNegocioException("CONSTANCIA_EMITIDA", "La constancia ya fue emitida.");
        }
        this.motivoRechazo = motivo;
        this.estado = EstadoConstancia.RECHAZADA;
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

    public List<FirmaDigital> getFirmas() {
        return List.copyOf(firmas);
    }
}
