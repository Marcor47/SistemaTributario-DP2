package pe.municipalidad.tributario.etl;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Decisión de homologación auditada; no se puede modificar desde la aplicación (RF-053). Las automáticas
 * las registra el motor en Python; las humanas salen de CasoHomologacion. EntidadMaestra 1 registra *;
 * CasoHomologacion 1 se resuelve con 0..1; * decide 0..1 Usuario.
 */
@Entity
@Immutable
@Table(name = "decision_homologacion", schema = "analitica")
public class DecisionHomologacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_decision")
    private Long idDecision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_maestro", nullable = false)
    private EntidadMaestra entidadMaestra;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", unique = true)
    private CasoHomologacion caso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_resolutor", nullable = false, length = 30)
    private NivelHomologacion nivelResolutor;

    @Column(name = "puntaje", precision = 5, scale = 4)
    private BigDecimal puntaje;

    @Column(name = "atributos_coincidentes", columnDefinition = "text")
    private String atributosCoincidentes;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_decision", nullable = false, length = 10)
    private TipoDecision tipoDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    /* Motivo de aprobación, rechazo u override; obligatorio en las decisiones humanas. */
    @Column(name = "motivo", length = 400)
    private String motivo;

    protected DecisionHomologacion() {
    }

    static DecisionHomologacion humana(CasoHomologacion caso, EntidadMaestra entidadMaestra, Usuario usuario,
            String motivo, LocalDateTime ahora) {
        if (usuario == null || motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("MOTIVO_REQUERIDO",
                    "Una decisión humana de homologación requiere al usuario y su motivo.");
        }
        DecisionHomologacion decision = new DecisionHomologacion();
        decision.caso = caso;
        decision.entidadMaestra = entidadMaestra;
        decision.nivelResolutor = caso.getNivelAlcanzado();
        decision.puntaje = caso.getPuntaje();
        decision.atributosCoincidentes = caso.getAtributosComparados();
        decision.tipoDecision = TipoDecision.HUMANA;
        decision.usuario = usuario;
        decision.motivo = motivo;
        decision.fechaHora = ahora;
        return decision;
    }

    public Long getIdDecision() {
        return idDecision;
    }

    public EntidadMaestra getEntidadMaestra() {
        return entidadMaestra;
    }

    public CasoHomologacion getCaso() {
        return caso;
    }

    public NivelHomologacion getNivelResolutor() {
        return nivelResolutor;
    }

    public BigDecimal getPuntaje() {
        return puntaje;
    }

    public String getAtributosCoincidentes() {
        return atributosCoincidentes;
    }

    public TipoDecision getTipoDecision() {
        return tipoDecision;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getMotivo() {
        return motivo;
    }
}
