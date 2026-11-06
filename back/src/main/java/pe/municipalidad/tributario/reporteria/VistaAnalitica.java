package pe.municipalidad.tributario.reporteria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.seguridad.Usuario;

/* Vista de la plataforma analítica guardada por su dueño; solo él la abre (RF-008, RF-044). */
@Entity
@Table(name = "vista_analitica", schema = "operativo")
public class VistaAnalitica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vista")
    private Long idVista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "estado", nullable = false, columnDefinition = "text")
    private String estado;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected VistaAnalitica() {
    }

    public VistaAnalitica(Usuario usuario, String nombre, String estado, LocalDateTime fechaActualizacion) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.estado = estado;
        this.fechaActualizacion = fechaActualizacion;
    }

    public void actualizar(String nuevoEstado, LocalDateTime fecha) {
        this.estado = nuevoEstado;
        this.fechaActualizacion = fecha;
    }

    public Long getIdVista() {
        return idVista;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
