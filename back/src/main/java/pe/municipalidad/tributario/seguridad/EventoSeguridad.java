package pe.municipalidad.tributario.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/* Inicios de sesión, fallos, cambios de rol y accesos denegados; se conservan al menos 90 días (RNF-024). */
@Entity
@Immutable
@Table(name = "evento_seguridad", schema = "seguridad")
public class EventoSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 25)
    private TipoEventoSeguridad tipoEvento;

    /* Usuario interno o documento del contribuyente involucrado. */
    @Column(name = "usuario", nullable = false, length = 40)
    private String usuario;

    @Column(name = "ip_origen", nullable = false, length = 45)
    private String ipOrigen;

    @Column(name = "detalle", length = 400)
    private String detalle;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected EventoSeguridad() {
    }

    public EventoSeguridad(TipoEventoSeguridad tipoEvento, String usuario, String ipOrigen, String detalle,
            LocalDateTime fechaHora) {
        this.tipoEvento = tipoEvento;
        this.usuario = usuario;
        this.ipOrigen = ipOrigen;
        this.detalle = detalle;
        this.fechaHora = fechaHora;
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public TipoEventoSeguridad getTipoEvento() {
        return tipoEvento;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }

    public String getDetalle() {
        return detalle;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
