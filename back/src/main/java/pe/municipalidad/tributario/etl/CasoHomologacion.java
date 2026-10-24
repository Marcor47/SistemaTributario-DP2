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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Caso que la cascada determinista deja pendiente y que revisa el Responsable de datos en la bandeja
 * (RF-040). Cada resolución produce una DecisionHomologacion inalterable (RF-053).
 */
@Entity
@Table(name = "caso_homologacion", schema = "analitica")
public class CasoHomologacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caso")
    private Long idCaso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lote", nullable = false)
    private LoteETL lote;

    /* CasoHomologacion * candidato 0..1 EntidadMaestra */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_maestro_candidato")
    private EntidadMaestra maestroCandidato;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_alcanzado", nullable = false, length = 30)
    private NivelHomologacion nivelAlcanzado;

    @Column(name = "puntaje", precision = 5, scale = 4)
    private BigDecimal puntaje;

    /* Atributos comparados y evidencia del motor, en JSON. */
    @Column(name = "atributos_comparados", nullable = false, columnDefinition = "text")
    private String atributosComparados;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoCaso estado;

    protected CasoHomologacion() {
    }

    public CasoHomologacion(LoteETL lote, EntidadMaestra maestroCandidato, NivelHomologacion nivelAlcanzado,
            BigDecimal puntaje, String atributosComparados) {
        this.lote = lote;
        this.maestroCandidato = maestroCandidato;
        this.nivelAlcanzado = nivelAlcanzado;
        this.puntaje = puntaje;
        this.atributosComparados = atributosComparados;
        this.estado = EstadoCaso.PENDIENTE;
    }

    /* Confirma la coincidencia con el candidato. */
    public DecisionHomologacion aprobar(Usuario usuario, String motivo, LocalDateTime ahora) {
        exigirPendiente();
        if (maestroCandidato == null) {
            throw new ReglaNegocioException("SIN_CANDIDATO", "El caso no tiene una entidad maestra candidata.");
        }
        estado = EstadoCaso.APROBADO;
        return DecisionHomologacion.humana(this, maestroCandidato, usuario, motivo, ahora);
    }

    /* Rechaza la coincidencia: el registro queda como una entidad nueva. */
    public DecisionHomologacion rechazar(EntidadMaestra entidadNueva, Usuario usuario, String motivo,
            LocalDateTime ahora) {
        exigirPendiente();
        estado = EstadoCaso.RECHAZADO;
        return DecisionHomologacion.humana(this, entidadNueva, usuario, motivo, ahora);
    }

    /* Asocia el registro a otra entidad maestra distinta del candidato. */
    public DecisionHomologacion reasignar(EntidadMaestra maestro, Usuario usuario, String motivo,
            LocalDateTime ahora) {
        exigirPendiente();
        estado = EstadoCaso.REASIGNADO;
        return DecisionHomologacion.humana(this, maestro, usuario, motivo, ahora);
    }

    private void exigirPendiente() {
        if (estado != EstadoCaso.PENDIENTE) {
            throw new ReglaNegocioException("CASO_RESUELTO", "El caso ya fue " + estado + ".");
        }
    }

    public Long getIdCaso() {
        return idCaso;
    }

    public LoteETL getLote() {
        return lote;
    }

    public EntidadMaestra getMaestroCandidato() {
        return maestroCandidato;
    }

    public NivelHomologacion getNivelAlcanzado() {
        return nivelAlcanzado;
    }

    public BigDecimal getPuntaje() {
        return puntaje;
    }

    public String getAtributosComparados() {
        return atributosComparados;
    }

    public EstadoCaso getEstado() {
        return estado;
    }
}
