package pe.municipalidad.tributario.reclamo;

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
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Registro de cada derivación de un reclamo: área destino, encargado de revisión y gestor que derivó (RF-064).
 * Solo se inserta; un trigger de la BD rechaza cambios y borrados.
 */
@Entity
@Immutable
@Table(name = "derivacion_reclamo", schema = "operativo")
public class DerivacionReclamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_derivacion")
    private Long idDerivacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reclamo", nullable = false)
    private Reclamo reclamo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_area_destino", nullable = false)
    private AreaMunicipal areaDestino;

    /* Encargado de revisión en el área destino. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_responsable", nullable = false)
    private Usuario responsable;

    /* Gestor de reclamos que derivó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_derivado_por", nullable = false)
    private Usuario derivadoPor;

    @Column(name = "fecha_derivacion", nullable = false)
    private LocalDateTime fechaDerivacion;

    @Column(name = "observacion", length = 400)
    private String observacion;

    protected DerivacionReclamo() {
    }

    DerivacionReclamo(Reclamo reclamo, AreaMunicipal areaDestino, Usuario responsable, Usuario derivadoPor,
            String observacion, LocalDateTime fechaDerivacion) {
        this.reclamo = reclamo;
        this.areaDestino = areaDestino;
        this.responsable = responsable;
        this.derivadoPor = derivadoPor;
        this.observacion = observacion;
        this.fechaDerivacion = fechaDerivacion;
    }

    public Long getIdDerivacion() {
        return idDerivacion;
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public AreaMunicipal getAreaDestino() {
        return areaDestino;
    }

    public Usuario getResponsable() {
        return responsable;
    }

    public Usuario getDerivadoPor() {
        return derivadoPor;
    }

    public LocalDateTime getFechaDerivacion() {
        return fechaDerivacion;
    }

    public String getObservacion() {
        return observacion;
    }
}
