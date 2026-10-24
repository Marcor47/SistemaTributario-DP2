package pe.municipalidad.tributario.tramitepredio;

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
import java.util.ArrayList;
import java.util.List;

/*
 * Archivo adjunto a una solicitud: solo PDF, JPG o PNG validados por contenido, hasta 10 MB, guardados
 * fuera de la raíz web y cifrados (RF-061, RNF-030, RNF-031). DocumentoAdjunto * satisface 1 RequisitoDocumental.
 */
@Entity
@Table(name = "documento_adjunto", schema = "operativo")
public class DocumentoAdjunto {

    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_adjunto")
    private Long idAdjunto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudPredio solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_requisito", nullable = false)
    private RequisitoDocumental requisito;

    @Column(name = "nombre_original", nullable = false, length = 150)
    private String nombreOriginal;

    /* Nombre generado por el sistema (UUID); nunca se usa el nombre original para guardar. */
    @Column(name = "nombre_generado", nullable = false, unique = true, length = 64)
    private String nombreGenerado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contenido", nullable = false, length = 5)
    private TipoContenido tipoContenido;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "ruta_cifrada", nullable = false, length = 250)
    private String rutaCifrada;

    @Column(name = "hash_contenido", nullable = false, length = 64)
    private String hashContenido;

    protected DocumentoAdjunto() {
    }

    public DocumentoAdjunto(RequisitoDocumental requisito, String nombreOriginal, String nombreGenerado,
            TipoContenido tipoContenido, long tamanoBytes, String rutaCifrada, String hashContenido) {
        this.requisito = requisito;
        this.nombreOriginal = nombreOriginal;
        this.nombreGenerado = nombreGenerado;
        this.tipoContenido = tipoContenido;
        this.tamanoBytes = tamanoBytes;
        this.rutaCifrada = rutaCifrada;
        this.hashContenido = hashContenido;
    }

    void asignarA(SolicitudPredio solicitud) {
        this.solicitud = solicitud;
    }

    /* Errores de tamaño; el tipo ya llega validado por contenido como TipoContenido. */
    public List<String> validar() {
        List<String> errores = new ArrayList<>();
        if (tamanoBytes <= 0 || tamanoBytes > TAMANO_MAXIMO_BYTES) {
            errores.add(nombreOriginal + ": el archivo debe pesar entre 1 byte y 10 MB");
        }
        return errores;
    }

    public Long getIdAdjunto() {
        return idAdjunto;
    }

    public SolicitudPredio getSolicitud() {
        return solicitud;
    }

    public RequisitoDocumental getRequisito() {
        return requisito;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getNombreGenerado() {
        return nombreGenerado;
    }

    public TipoContenido getTipoContenido() {
        return tipoContenido;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public String getRutaCifrada() {
        return rutaCifrada;
    }

    public String getHashContenido() {
        return hashContenido;
    }
}
