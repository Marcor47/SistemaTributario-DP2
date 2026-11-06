package pe.municipalidad.tributario.constancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Constancia de No Adeudo emitida con el formato oficial y la firma digital de la Municipalidad
 * (RF-037, RF-046, RF-055). SolicitudConstancia 1 emite 0..1 Constancia; Constancia * usa 1 VersionPlantilla.
 * La generación del PDF (generar/descargar) la hace el servicio de constancias con la plantilla vigente.
 */
@Entity
@Table(name = "constancia", schema = "operativo")
public class Constancia {

    @Id
    @Column(name = "numero_constancia", length = 20)
    private String numeroConstancia;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false, unique = true)
    private SolicitudConstancia solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_version_plantilla", nullable = false)
    private VersionPlantilla versionPlantilla;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "predios_incluidos", nullable = false, length = 400)
    private String prediosIncluidos;

    @Column(name = "rango_fechas", nullable = false, length = 60)
    private String rangoFechas;

    @Column(name = "archivo_pdf", nullable = false, length = 250)
    private String archivoPdf;

    /* Lleva la marca "DOCUMENTO DE PRUEBA" si se emitió con un servicio simulado (RNF-015). */
    @Column(name = "es_de_prueba", nullable = false)
    private boolean esDePrueba;

    @Column(name = "contenido_pdf")
    private byte[] contenidoPdf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador_emisor")
    private Usuario trabajadorEmisor;

    protected Constancia() {
    }

    public Constancia(String numeroConstancia, SolicitudConstancia solicitud, VersionPlantilla versionPlantilla,
            LocalDateTime fechaEmision, String prediosIncluidos, String rangoFechas, String archivoPdf,
            boolean esDePrueba, Usuario trabajadorEmisor, byte[] contenidoPdf) {
        this.numeroConstancia = numeroConstancia;
        this.solicitud = solicitud;
        this.versionPlantilla = versionPlantilla;
        this.fechaEmision = fechaEmision;
        this.prediosIncluidos = prediosIncluidos;
        this.rangoFechas = rangoFechas;
        this.archivoPdf = archivoPdf;
        this.esDePrueba = esDePrueba;
        this.trabajadorEmisor = trabajadorEmisor;
        this.contenidoPdf = contenidoPdf.clone();
    }

    public String getNumeroConstancia() {
        return numeroConstancia;
    }

    public SolicitudConstancia getSolicitud() {
        return solicitud;
    }

    public VersionPlantilla getVersionPlantilla() {
        return versionPlantilla;
    }

    public LocalDateTime getFechaEmision() {
        return fechaEmision;
    }

    public String getPrediosIncluidos() {
        return prediosIncluidos;
    }

    public String getRangoFechas() {
        return rangoFechas;
    }

    public String getArchivoPdf() {
        return archivoPdf;
    }

    public byte[] getContenidoPdf() {
        return contenidoPdf == null ? null : contenidoPdf.clone();
    }

    public boolean isEsDePrueba() {
        return esDePrueba;
    }

    public Usuario getTrabajadorEmisor() {
        return trabajadorEmisor;
    }
}
