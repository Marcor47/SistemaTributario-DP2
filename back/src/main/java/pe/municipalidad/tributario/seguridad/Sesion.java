package pe.municipalidad.tributario.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/*
 * Sesión propia del sistema, para usuarios internos y contribuyentes; al contribuyente no se le entrega
 * el token municipal (RF-054, RNF-022, RNF-028). Usuario 1 abre * Sesion (solo las internas).
 */
@Entity
@Table(name = "sesion", schema = "seguridad")
public class Sesion {

    @Id
    @Column(name = "id_sesion", length = 64)
    private String idSesion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sesion", nullable = false, length = 15)
    private TipoSesion tipoSesion;

    /* Usuario interno o documento del contribuyente. */
    @Column(name = "identificador", nullable = false, length = 40)
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "ultima_actividad", nullable = false)
    private LocalDateTime ultimaActividad;

    @Column(name = "minutos_inactividad", nullable = false)
    private int minutosInactividad;

    @Column(name = "ip_origen", nullable = false, length = 45)
    private String ipOrigen;

    protected Sesion() {
    }

    private Sesion(String idSesion, TipoSesion tipoSesion, String identificador, Usuario usuario, String ipOrigen,
            LocalDateTime ahora) {
        this.idSesion = idSesion;
        this.tipoSesion = tipoSesion;
        this.identificador = identificador;
        this.usuario = usuario;
        this.fechaInicio = ahora;
        this.ultimaActividad = ahora;
        this.minutosInactividad = tipoSesion.getMinutosInactividad();
        this.ipOrigen = ipOrigen;
    }

    public static Sesion interna(String idSesion, Usuario usuario, String ipOrigen, LocalDateTime ahora) {
        return new Sesion(idSesion, TipoSesion.INTERNA, usuario.getNombreUsuario(), usuario, ipOrigen, ahora);
    }

    public static Sesion deContribuyente(String idSesion, String documento, String ipOrigen, LocalDateTime ahora) {
        return new Sesion(idSesion, TipoSesion.CONTRIBUYENTE, documento, null, ipOrigen, ahora);
    }

    public void renovar(LocalDateTime ahora) {
        this.ultimaActividad = ahora;
    }

    /* La operación cerrar() del modelo la realiza el servicio de sesiones eliminando el registro. */
    public boolean haExpirado(LocalDateTime ahora) {
        return ultimaActividad.plusMinutes(minutosInactividad).isBefore(ahora);
    }

    public String getIdSesion() {
        return idSesion;
    }

    public TipoSesion getTipoSesion() {
        return tipoSesion;
    }

    public String getIdentificador() {
        return identificador;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public LocalDateTime getUltimaActividad() {
        return ultimaActividad;
    }

    public int getMinutosInactividad() {
        return minutosInactividad;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }
}
