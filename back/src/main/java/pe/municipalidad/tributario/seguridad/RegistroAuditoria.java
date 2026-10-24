package pe.municipalidad.tributario.seguridad;

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
import org.hibernate.annotations.Immutable;

/*
 * Historial de auditoría (RNF-008, RNF-033). Solo se inserta: un trigger de la BD rechaza cambios y borrados.
 * Usuario 1 genera * RegistroAuditoria; usuario nulo = acción del sistema.
 */
@Entity
@Immutable
@Table(name = "registro_auditoria", schema = "seguridad")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long idRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "accion", nullable = false, length = 60)
    private String accion;

    /* Entidad afectada o dato consultado. */
    @Column(name = "entidad", nullable = false, length = 120)
    private String entidad;

    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text")
    private String valorNuevo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Usuario usuario, String accion, String entidad, String valorAnterior, String valorNuevo,
            LocalDateTime fechaHora) {
        this.usuario = usuario;
        this.accion = accion;
        this.entidad = entidad;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.fechaHora = fechaHora;
    }

    public Long getIdRegistro() {
        return idRegistro;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
