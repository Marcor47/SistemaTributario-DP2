package pe.municipalidad.tributario.reclamo;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Reclamo de un contribuyente (cobranzas incorrectas, servicio o atención) o de un trabajador interno
 * (incidentes laborales) (RF-064, HU050). El Gestor de reclamos lo deriva al área correspondiente hasta que se
 * atienda; cada derivación queda registrada. Reclamo 1 ◆ * DerivacionReclamo.
 */
@Entity
@Table(name = "reclamo", schema = "operativo")
public class Reclamo {

    public static final int MAXIMO_CARACTERES_COMENTARIO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reclamo")
    private Long idReclamo;

    /* Asignado por el sistema con operativo.fn_siguiente_numero_reclamo (REC-AAAA-NNNNNN). */
    @Column(name = "numero_reclamo", nullable = false, unique = true, length = 20)
    private String numeroReclamo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reclamante", nullable = false, length = 15)
    private TipoReclamante tipoReclamante;

    /* DNI o RUC, si reclama un contribuyente. */
    @Column(name = "documento_contribuyente", length = 12)
    private String documentoContribuyente;

    /* Usuario interno, si reclama un trabajador. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_reclamante")
    private Usuario usuarioReclamante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reclamo", nullable = false, length = 20)
    private TipoReclamo tipoReclamo;

    /* Documento o servicio al que se refiere. */
    @Column(name = "referencia", length = 150)
    private String referencia;

    @Column(name = "fecha_hora_incidente", nullable = false)
    private LocalDateTime fechaHoraIncidente;

    @Column(name = "comentario", nullable = false, columnDefinition = "text")
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoReclamo estado;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_area_actual")
    private AreaMunicipal areaActual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_actual")
    private Usuario responsableActual;

    @Column(name = "respuesta", columnDefinition = "text")
    private String respuesta;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @OneToMany(mappedBy = "reclamo", cascade = CascadeType.PERSIST)
    @OrderBy("fechaDerivacion ASC")
    private List<DerivacionReclamo> derivaciones = new ArrayList<>();

    protected Reclamo() {
    }

    private Reclamo(String numeroReclamo, TipoReclamante tipoReclamante, TipoReclamo tipoReclamo, String referencia,
            LocalDateTime fechaHoraIncidente, String comentario, LocalDateTime ahora) {
        if (!tipoReclamo.permitidoPara(tipoReclamante)) {
            throw new ReglaNegocioException("TIPO_RECLAMO_NO_PERMITIDO",
                    "El tipo " + tipoReclamo + " no está disponible para un reclamo de " + tipoReclamante + ".");
        }
        if (fechaHoraIncidente.isAfter(ahora)) {
            throw new ReglaNegocioException("FECHA_INCIDENTE_FUTURA", "La fecha del incidente no puede ser futura.");
        }
        if (comentario == null || comentario.isBlank() || comentario.length() > MAXIMO_CARACTERES_COMENTARIO) {
            throw new ReglaNegocioException("COMENTARIO_INVALIDO",
                    "El comentario es obligatorio y admite hasta " + MAXIMO_CARACTERES_COMENTARIO + " caracteres.");
        }
        this.numeroReclamo = numeroReclamo;
        this.tipoReclamante = tipoReclamante;
        this.tipoReclamo = tipoReclamo;
        this.referencia = referencia;
        this.fechaHoraIncidente = fechaHoraIncidente;
        this.comentario = comentario;
        this.fechaRegistro = ahora;
        this.estado = EstadoReclamo.ENVIADO;
    }

    public static Reclamo deContribuyente(String numeroReclamo, String documentoContribuyente, TipoReclamo tipoReclamo,
            String referencia, LocalDateTime fechaHoraIncidente, String comentario, LocalDateTime ahora) {
        Reclamo reclamo = new Reclamo(numeroReclamo, TipoReclamante.CONTRIBUYENTE, tipoReclamo, referencia,
                fechaHoraIncidente, comentario, ahora);
        reclamo.documentoContribuyente = documentoContribuyente;
        return reclamo;
    }

    public static Reclamo deTrabajador(String numeroReclamo, Usuario trabajador, TipoReclamo tipoReclamo,
            String referencia, LocalDateTime fechaHoraIncidente, String comentario, LocalDateTime ahora) {
        Reclamo reclamo = new Reclamo(numeroReclamo, TipoReclamante.TRABAJADOR, tipoReclamo, referencia,
                fechaHoraIncidente, comentario, ahora);
        reclamo.usuarioReclamante = trabajador;
        return reclamo;
    }

    /* El Gestor de reclamos deriva al área y al encargado de revisión; se puede volver a derivar. */
    public DerivacionReclamo derivar(AreaMunicipal area, Usuario responsable, Usuario gestor, String observacion,
            LocalDateTime ahora) {
        exigirAbierto();
        if (gestor.getRoles().stream().noneMatch(r -> r.getNombre() == NombreRol.GESTOR_RECLAMOS)) {
            throw new ReglaNegocioException("GESTOR_REQUERIDO", "Solo el Gestor de reclamos puede derivar un reclamo.");
        }
        DerivacionReclamo derivacion = new DerivacionReclamo(this, area, responsable, gestor, observacion, ahora);
        derivaciones.add(derivacion);
        this.areaActual = area;
        this.responsableActual = responsable;
        this.estado = EstadoReclamo.EN_REVISION;
        return derivacion;
    }

    /* El encargado de revisión del área responde y el reclamo queda atendido. */
    public void responder(String textoRespuesta, LocalDateTime ahora) {
        if (estado != EstadoReclamo.EN_REVISION) {
            throw new ReglaNegocioException("RECLAMO_NO_EN_REVISION", "Solo se responde un reclamo en revisión.");
        }
        if (textoRespuesta == null || textoRespuesta.isBlank()) {
            throw new ReglaNegocioException("RESPUESTA_REQUERIDA", "La respuesta es obligatoria.");
        }
        this.respuesta = textoRespuesta;
        this.fechaRespuesta = ahora;
        this.estado = EstadoReclamo.APROBADO_CON_RESPUESTA;
    }

    public void cancelar() {
        exigirAbierto();
        this.estado = EstadoReclamo.CANCELADO;
    }

    public EstadoReclamo consultarEstado() {
        return estado;
    }

    private void exigirAbierto() {
        if (estado == EstadoReclamo.APROBADO_CON_RESPUESTA || estado == EstadoReclamo.CANCELADO) {
            throw new ReglaNegocioException("RECLAMO_CERRADO", "El reclamo ya fue " + estado + ".");
        }
    }

    public Long getIdReclamo() {
        return idReclamo;
    }

    public String getNumeroReclamo() {
        return numeroReclamo;
    }

    public TipoReclamante getTipoReclamante() {
        return tipoReclamante;
    }

    public String getDocumentoContribuyente() {
        return documentoContribuyente;
    }

    public Usuario getUsuarioReclamante() {
        return usuarioReclamante;
    }

    public TipoReclamo getTipoReclamo() {
        return tipoReclamo;
    }

    public String getReferencia() {
        return referencia;
    }

    public LocalDateTime getFechaHoraIncidente() {
        return fechaHoraIncidente;
    }

    public String getComentario() {
        return comentario;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public AreaMunicipal getAreaActual() {
        return areaActual;
    }

    public Usuario getResponsableActual() {
        return responsableActual;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    public List<DerivacionReclamo> getDerivaciones() {
        return List.copyOf(derivaciones);
    }
}
