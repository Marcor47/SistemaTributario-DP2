package pe.municipalidad.tributario.tramitepredio;

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
import java.util.Set;
import java.util.stream.Collectors;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Solicitud del contribuyente para registrar o traspasar un predio; siempre la revisa una persona de
 * ventanilla (RF-061, RF-056, RF-048). SolicitudPredio * presenta 1 Contribuyente (documentoContribuyente);
 * * registra o traspasa 0..1 Predio (codigoPredio); 1 ◆ 1..* DocumentoAdjunto.
 */
@Entity
@Table(name = "solicitud_predio", schema = "operativo")
public class SolicitudPredio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tramite", nullable = false, length = 10)
    private TipoTramitePredio tipoTramite;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_predio", nullable = false, length = 10)
    private TipoPredio tipoPredio;

    @Column(name = "documento_contribuyente", nullable = false, length = 12)
    private String documentoContribuyente;

    @Column(name = "codigo_predio", length = 12)
    private String codigoPredio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoSolicitudPredio estado;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "firma_electronica", nullable = false)
    private boolean firmaElectronica;

    @Column(name = "motivo_rechazo", length = 400)
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisor")
    private Usuario revisor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    /* HU043: deuda existente del predio cuando se decidió; la respuesta del buzón muestra esta cifra. */
    @Column(name = "deuda_predio", precision = 14, scale = 2)
    private BigDecimal deudaPredio;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentoAdjunto> adjuntos = new ArrayList<>();

    protected SolicitudPredio() {
    }

    public SolicitudPredio(TipoTramitePredio tipoTramite, TipoPredio tipoPredio, String documentoContribuyente,
            String codigoPredio, boolean firmaElectronica) {
        this.tipoTramite = tipoTramite;
        this.tipoPredio = tipoPredio;
        this.documentoContribuyente = documentoContribuyente;
        this.codigoPredio = codigoPredio;
        this.firmaElectronica = firmaElectronica;
    }

    public void adjuntar(DocumentoAdjunto adjunto) {
        adjunto.asignarA(this);
        adjuntos.add(adjunto);
    }

    /* Valida los adjuntos contra los requisitos obligatorios y la deja en la bandeja de ventanilla. */
    public void enviar(List<RequisitoDocumental> requisitos, LocalDateTime ahora) {
        if (tipoTramite == TipoTramitePredio.TRASPASO && codigoPredio == null) {
            throw new ReglaNegocioException("PREDIO_REQUERIDO", "El traspaso requiere el código del predio.");
        }
        Set<Long> cumplidos = adjuntos.stream().map(a -> a.getRequisito().getIdRequisito()).collect(Collectors.toSet());
        List<String> faltantes = requisitos.stream()
                .filter(RequisitoDocumental::isObligatorio)
                .filter(r -> !cumplidos.contains(r.getIdRequisito()))
                .map(RequisitoDocumental::getNombreDocumento)
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("REQUISITOS_FALTANTES",
                    "Faltan documentos obligatorios: " + String.join("; ", faltantes) + ".");
        }
        this.estado = EstadoSolicitudPredio.EN_REVISION;
        this.fechaEnvio = ahora;
    }

    public EstadoSolicitudPredio consultarEstado() {
        return estado;
    }

    /*
     * HU043: se acepta después de registrar o traspasar el predio en SAMNET. La revisión siempre es humana, así que
     * el revisor es obligatorio y queda registrado con la fecha.
     */
    public void aceptar(Usuario revisor, String codigoPredioRegistrado, BigDecimal deudaPredio, LocalDateTime ahora) {
        exigirEnRevision();
        registrarRevision(revisor, deudaPredio, ahora);
        this.codigoPredio = codigoPredioRegistrado;
        this.estado = EstadoSolicitudPredio.ACEPTADA;
    }

    public void rechazar(Usuario revisor, String motivo, BigDecimal deudaPredio, LocalDateTime ahora) {
        exigirEnRevision();
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("MOTIVO_REQUERIDO", "El rechazo requiere un motivo.");
        }
        registrarRevision(revisor, deudaPredio, ahora);
        this.motivoRechazo = motivo;
        this.estado = EstadoSolicitudPredio.RECHAZADA;
    }

    private void registrarRevision(Usuario revisor, BigDecimal deudaPredio, LocalDateTime ahora) {
        if (revisor == null) {
            throw new ReglaNegocioException("REVISOR_REQUERIDO", "La revisión la realiza un usuario de ventanilla.");
        }
        this.revisor = revisor;
        this.deudaPredio = deudaPredio;
        this.fechaRevision = ahora;
    }

    /* El servicio la exige antes de escribir en SAMNET, para no registrar un predio de una solicitud ya cerrada. */
    public void exigirEnRevision() {
        if (estado != EstadoSolicitudPredio.EN_REVISION) {
            throw new ReglaNegocioException("SOLICITUD_NO_EN_REVISION", "La solicitud ya fue " + estado + ".");
        }
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public TipoTramitePredio getTipoTramite() {
        return tipoTramite;
    }

    public TipoPredio getTipoPredio() {
        return tipoPredio;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public String getCodigoPredio() {
        return codigoPredio;
    }

    public EstadoSolicitudPredio getEstado() {
        return estado;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public boolean isFirmaElectronica() {
        return firmaElectronica;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public Usuario getRevisor() {
        return revisor;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public BigDecimal getDeudaPredio() {
        return deudaPredio;
    }

    public List<DocumentoAdjunto> getAdjuntos() {
        return List.copyOf(adjuntos);
    }
}
