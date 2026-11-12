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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.constancia.Constancia;
import pe.municipalidad.tributario.reclamo.Reclamo;
import pe.municipalidad.tributario.reclamo.TipoReclamante;
import pe.municipalidad.tributario.tramitepredio.SolicitudPredio;

/*
 * Documento en el buzón del contribuyente: constancias, estados de cuenta y respuestas a solicitudes y a
 * reclamos, con la fecha de envío y la de primera lectura (RF-030, RF-064).
 */
@Entity
@Table(name = "documento_buzon", schema = "operativo")
public class DocumentoBuzon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Long idDocumento;

    /* Destinatario: DNI o RUC del contribuyente (DocumentoBuzon * para 1 Contribuyente). */
    @Column(name = "documento_contribuyente", nullable = false, length = 12)
    private String documentoContribuyente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDocumentoBuzon tipo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "archivo", length = 250)
    private String archivo;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_primera_lectura")
    private LocalDateTime fechaPrimeraLectura;

    @Column(name = "leido_actualmente", nullable = false)
    private boolean leidoActualmente;

    @Column(name = "destacado", nullable = false)
    private boolean destacado;

    @Column(name = "archivado", nullable = false)
    private boolean archivado;

    @Column(name = "fecha_archivado")
    private LocalDateTime fechaArchivado;

    @Column(name = "contenido", columnDefinition = "text")
    private String contenido;

    /* Constancia 1 se deposita 0..1 DocumentoBuzon */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "numero_constancia", unique = true)
    private Constancia constancia;

    /* SolicitudPredio 1 respuesta 0..1 DocumentoBuzon */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud_predio", unique = true)
    private SolicitudPredio solicitudPredio;

    /* Un reclamo puede generar el aviso de primera asignación y su respuesta final. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reclamo")
    private Reclamo reclamo;

    protected DocumentoBuzon() {
    }

    private DocumentoBuzon(String documentoContribuyente, TipoDocumentoBuzon tipo, String titulo, String archivo,
            LocalDateTime fechaEnvio) {
        this.documentoContribuyente = documentoContribuyente;
        this.tipo = tipo;
        this.titulo = titulo;
        this.archivo = archivo;
        this.fechaEnvio = fechaEnvio;
    }

    public static DocumentoBuzon deConstancia(Constancia constancia, String documentoContribuyente,
            LocalDateTime fechaEnvio) {
        DocumentoBuzon documento = new DocumentoBuzon(documentoContribuyente, TipoDocumentoBuzon.CONSTANCIA,
                "Constancia de No Adeudo " + constancia.getNumeroConstancia(), constancia.getArchivoPdf(),
                fechaEnvio);
        documento.constancia = constancia;
        return documento;
    }

    public static DocumentoBuzon respuestaASolicitud(SolicitudPredio solicitud, String titulo, String archivo,
            LocalDateTime fechaEnvio) {
        DocumentoBuzon documento = new DocumentoBuzon(solicitud.getDocumentoContribuyente(),
                TipoDocumentoBuzon.RESPUESTA_SOLICITUD, titulo, archivo, fechaEnvio);
        documento.solicitudPredio = solicitud;
        documento.contenido = "Solicitud #" + solicitud.getIdSolicitud() + " · " + solicitud.getTipoTramite()
                + " · " + solicitud.getEstado()
                + (solicitud.getCodigoPredio() == null ? "" : "\nPredio: " + solicitud.getCodigoPredio())
                + (solicitud.getMotivoRechazo() == null ? "" : "\nMotivo: " + solicitud.getMotivoRechazo())
                + (solicitud.getDeudaPredio() == null ? ""
                        : "\nDeuda existente del predio: S/ " + solicitud.getDeudaPredio().toPlainString());
        return documento;
    }

    /* Solo los reclamos de contribuyentes llegan al buzón; los internos se consultan en la plataforma. */
    public static DocumentoBuzon respuestaAReclamo(Reclamo reclamo, String archivo, LocalDateTime fechaEnvio) {
        if (reclamo.getTipoReclamante() != TipoReclamante.CONTRIBUYENTE) {
            throw new ReglaNegocioException("RECLAMO_INTERNO", "La respuesta a un reclamo interno no va al buzón.");
        }
        DocumentoBuzon documento = new DocumentoBuzon(reclamo.getDocumentoContribuyente(),
                TipoDocumentoBuzon.RESPUESTA_RECLAMO, "Respuesta al reclamo " + reclamo.getNumeroReclamo(), archivo,
                fechaEnvio);
        documento.reclamo = reclamo;
        documento.contenido = reclamo.getRespuesta();
        return documento;
    }

    public static DocumentoBuzon asignacionDeReclamo(Reclamo reclamo, String area, LocalDateTime fechaEnvio) {
        if (reclamo.getTipoReclamante() != TipoReclamante.CONTRIBUYENTE) {
            throw new ReglaNegocioException("RECLAMO_INTERNO", "La asignación de un reclamo interno no va al buzón.");
        }
        DocumentoBuzon documento = new DocumentoBuzon(reclamo.getDocumentoContribuyente(),
                TipoDocumentoBuzon.ASIGNACION_RECLAMO, "Reclamo " + reclamo.getNumeroReclamo() + " en revisión",
                null, fechaEnvio);
        documento.reclamo = reclamo;
        documento.contenido = "Tu reclamo fue asignado al área " + area
                + ". Desde este momento se encuentra en revisión.";
        return documento;
    }

    public static DocumentoBuzon deEstadoCuenta(String documentoContribuyente, String titulo, String archivo,
            LocalDateTime fechaEnvio) {
        return new DocumentoBuzon(documentoContribuyente, TipoDocumentoBuzon.ESTADO_CUENTA, titulo, archivo,
                fechaEnvio);
    }

    /* Solo se registra la primera lectura. */
    public void marcarLeido(LocalDateTime ahora) {
        if (fechaPrimeraLectura == null) {
            fechaPrimeraLectura = ahora;
        }
        leidoActualmente = true;
    }

    public Long getIdDocumento() {
        return idDocumento;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public TipoDocumentoBuzon getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getArchivo() {
        return archivo;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public LocalDateTime getFechaPrimeraLectura() {
        return fechaPrimeraLectura;
    }

    public Constancia getConstancia() {
        return constancia;
    }

    public SolicitudPredio getSolicitudPredio() {
        return solicitudPredio;
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public String getContenido() {
        return contenido;
    }

    public void marcarNoLeido() {
        leidoActualmente = false;
    }

    public boolean isLeidoActualmente() {
        return leidoActualmente;
    }

    public boolean isDestacado() {
        return destacado;
    }

    public boolean isArchivado() {
        return archivado;
    }

    public LocalDateTime getFechaArchivado() {
        return fechaArchivado;
    }

    public void cambiarDestacado(boolean valor) {
        destacado = valor;
    }

    public void cambiarArchivado(boolean valor, LocalDateTime ahora) {
        archivado = valor;
        fechaArchivado = valor ? ahora : null;
    }
}
