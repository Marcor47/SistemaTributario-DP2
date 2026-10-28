package pe.municipalidad.tributario.documentos;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Versión de una plantilla; solo la vigente se usa para generar documentos (RF-063). Las anteriores se
 * conservan como historial. tipoContenido y contenido guardan el archivo publicado (HU049).
 */
@Entity
@Table(name = "version_plantilla", schema = "operativo")
public class VersionPlantilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_version")
    private Long idVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_plantilla", nullable = false)
    private PlantillaDocumento plantilla;

    @Column(name = "numero_version", nullable = false)
    private int numeroVersion;

    /* Nombre del archivo publicado. */
    @Column(name = "archivo", nullable = false, length = 255)
    private String archivo;

    @Column(name = "fecha_publicacion", nullable = false)
    private Instant fechaPublicacion;

    @Column(name = "vigente", nullable = false)
    private boolean vigente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_publicador")
    private Usuario publicador;

    @Column(name = "tipo_contenido", nullable = false, length = 120)
    private String tipoContenido;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "contenido", nullable = false, columnDefinition = "bytea")
    private byte[] contenido;

    protected VersionPlantilla() {
    }

    public VersionPlantilla(PlantillaDocumento plantilla, int numeroVersion, String archivo, String tipoContenido,
            byte[] contenido, Usuario publicador, Instant fechaPublicacion) {
        this.plantilla = plantilla;
        this.numeroVersion = numeroVersion;
        this.archivo = archivo;
        this.tipoContenido = tipoContenido;
        this.contenido = contenido;
        this.publicador = publicador;
        this.fechaPublicacion = fechaPublicacion;
        this.vigente = true;
    }

    /* Deja de ser la versión vigente al publicarse una nueva. */
    public void retirar() {
        this.vigente = false;
    }

    public byte[] descargar() {
        return contenido;
    }

    public Long getIdVersion() {
        return idVersion;
    }

    public PlantillaDocumento getPlantilla() {
        return plantilla;
    }

    public int getNumeroVersion() {
        return numeroVersion;
    }

    public String getArchivo() {
        return archivo;
    }

    public Instant getFechaPublicacion() {
        return fechaPublicacion;
    }

    public boolean isVigente() {
        return vigente;
    }

    public Usuario getPublicador() {
        return publicador;
    }

    public String getTipoContenido() {
        return tipoContenido;
    }
}
