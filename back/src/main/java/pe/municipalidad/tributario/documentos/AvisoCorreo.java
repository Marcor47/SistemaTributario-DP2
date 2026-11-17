package pe.municipalidad.tributario.documentos;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;

/*
 * Aviso por correo: mora temprana o recordatorio de vencimiento (RF-013, RF-022). Queda en el historial
 * del contribuyente. AvisoCorreo * dirigido a 1 Contribuyente; * refiere a 0..1 Obligacion (SAMNET).
 */
@Entity
@Table(name = "aviso_correo", schema = "operativo")
public class AvisoCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(AvisoCorreo.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aviso")
    private Long idAviso;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 25)
    private TipoAviso tipo;

    @Column(name = "documento_contribuyente", nullable = false, length = 12)
    private String documentoContribuyente;

    @Column(name = "correo_destino", nullable = false, length = 120)
    private String correoDestino;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoAviso estado;

    /* Lote del ETL que detectó la cuota vencida (mora temprana). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lote")
    private LoteETL lote;

    @Column(name = "id_obligacion", length = 20)
    private String idObligacion;

    protected AvisoCorreo() {
    }

    public AvisoCorreo(TipoAviso tipo, String documentoContribuyente, String correoDestino,
            LocalDateTime fechaProgramada, LoteETL lote, String idObligacion) {
        this.tipo = tipo;
        this.documentoContribuyente = documentoContribuyente;
        this.correoDestino = correoDestino;
        this.fechaProgramada = fechaProgramada;
        this.lote = lote;
        this.idObligacion = idObligacion;
        this.estado = EstadoAviso.PENDIENTE;
    }

    public void enviar(CorreoGateway correo, String asunto, String cuerpo, LocalDateTime ahora) {
        enviar(correo, asunto, cuerpo, null, ahora);
    }

    /*
     * HU016: el recordatorio puede incluir HTML; las alertas de mora (HU012) siguen usando el texto plano.
     * Si el envío falla, el aviso queda FALLIDO para que el siguiente procesamiento lo reintente.
     */
    public void enviar(CorreoGateway correo, String asunto, String cuerpo, String html, LocalDateTime ahora) {
        try {
            correo.enviarCorreo(correoDestino, asunto, cuerpo, html);
            this.estado = correo.registraSinEnviar() ? EstadoAviso.REGISTRADO : EstadoAviso.ENVIADO;
            this.fechaEnvio = ahora;
        } catch (RuntimeException ex) {
            this.estado = EstadoAviso.FALLIDO;
            LOG.warn("No se pudo enviar el aviso por correo '{}': {}", asunto, ex.getMessage());
        }
    }

    public Long getIdAviso() {
        return idAviso;
    }

    public TipoAviso getTipo() {
        return tipo;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public String getCorreoDestino() {
        return correoDestino;
    }

    public LocalDateTime getFechaProgramada() {
        return fechaProgramada;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public EstadoAviso getEstado() {
        return estado;
    }

    public LoteETL getLote() {
        return lote;
    }

    public String getIdObligacion() {
        return idObligacion;
    }
}
