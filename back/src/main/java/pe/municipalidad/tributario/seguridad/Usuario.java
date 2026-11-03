package pe.municipalidad.tributario.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/*
 * Persona interna autorizada a usar la plataforma web (RF-047). La clave se guarda solo como hash
 * adaptativo (RNF-020); la verificación la hace el servicio de autenticación con su PasswordEncoder.
 */
@Entity
@Table(name = "usuario", schema = "seguridad")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 40)
    private String nombreUsuario;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "correo", nullable = false, unique = true, length = 120)
    private String correo;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "cambio_clave_pendiente", nullable = false)
    private boolean cambioClavePendiente;

    @Column(name = "fecha_cambio_clave")
    private LocalDateTime fechaCambioClave;

    /* Usuario * tiene 1..* Rol */
    @ManyToMany
    @JoinTable(name = "usuario_rol", schema = "seguridad",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_rol"))
    private Set<Rol> roles = new HashSet<>();

    protected Usuario() {
    }

    public Usuario(String nombreUsuario, String nombreCompleto, String correo, String claveHash, Rol rolInicial,
            LocalDateTime fechaCreacion) {
        this(nombreUsuario, nombreCompleto, correo, claveHash, rolInicial, fechaCreacion, false);
    }

    public Usuario(String nombreUsuario, String nombreCompleto, String correo, String claveHash, Rol rolInicial,
            LocalDateTime fechaCreacion, boolean cambioClavePendiente) {
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.claveHash = claveHash;
        this.activo = true;
        this.fechaCreacion = fechaCreacion;
        this.cambioClavePendiente = cambioClavePendiente;
        this.fechaCambioClave = cambioClavePendiente ? null : fechaCreacion;
        this.roles.add(rolInicial);
    }

    public void cambiarClave(String nuevoHash, LocalDateTime fechaCambio) {
        this.claveHash = nuevoHash;
        this.cambioClavePendiente = false;
        this.fechaCambioClave = fechaCambio;
    }

    public void asignarRol(Rol rol) {
        roles.add(rol);
    }

    public void sustituirRol(Rol rol) {
        roles.clear();
        roles.add(rol);
    }

    public boolean tienePermiso(String modulo, String accion) {
        return activo && roles.stream().anyMatch(r -> r.tienePermiso(modulo, accion));
    }

    public void desactivar() {
        this.activo = false;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public String getClaveHash() {
        return claveHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public boolean isCambioClavePendiente() {
        return cambioClavePendiente;
    }

    public LocalDateTime getFechaCambioClave() {
        return fechaCambioClave;
    }

    public Set<Rol> getRoles() {
        return Set.copyOf(roles);
    }
}
