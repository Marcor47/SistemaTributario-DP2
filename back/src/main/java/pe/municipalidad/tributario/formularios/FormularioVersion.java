package pe.municipalidad.tributario.formularios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "formulario_version")
public class FormularioVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String codigo;
    @Column(nullable = false, length = 200)
    private String nombre;
    @Column(name = "version_numero", nullable = false)
    private int versionNumero;
    @Column(nullable = false)
    private boolean vigente;
    @Column(name = "fecha_publicacion", nullable = false)
    private Instant fechaPublicacion;
    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;
    @Column(name = "tipo_contenido", nullable = false, length = 120)
    private String tipoContenido;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] contenido;

    protected FormularioVersion() {}

    public FormularioVersion(String codigo, String nombre, int versionNumero, String nombreArchivo,
            String tipoContenido, byte[] contenido) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.versionNumero = versionNumero;
        this.nombreArchivo = nombreArchivo;
        this.tipoContenido = tipoContenido;
        this.contenido = contenido;
        this.vigente = true;
        this.fechaPublicacion = Instant.now();
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getVersionNumero() { return versionNumero; }
    public boolean isVigente() { return vigente; }
    public void setVigente(boolean vigente) { this.vigente = vigente; }
    public Instant getFechaPublicacion() { return fechaPublicacion; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getTipoContenido() { return tipoContenido; }
    public byte[] getContenido() { return contenido; }
}
