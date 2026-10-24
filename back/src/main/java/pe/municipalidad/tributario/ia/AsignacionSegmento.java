package pe.municipalidad.tributario.ia;

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
import pe.municipalidad.tributario.analitica.DimContribuyente;

/*
 * Segmento asignado a un contribuyente seudonimizado, con el segmento binario actual para compararlos
 * (RF-043). AsignacionSegmento * a 1 DimContribuyente.
 */
@Entity
@Table(name = "asignacion_segmento", schema = "analitica")
public class AsignacionSegmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Long idAsignacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_segmento", nullable = false)
    private Segmento segmento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seudonimo_contribuyente", nullable = false)
    private DimContribuyente contribuyente;

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion;

    @Column(name = "segmento_binario_actual", length = 30)
    private String segmentoBinarioActual;

    protected AsignacionSegmento() {
    }

    public AsignacionSegmento(Segmento segmento, DimContribuyente contribuyente, LocalDateTime fechaAsignacion) {
        this.segmento = segmento;
        this.contribuyente = contribuyente;
        this.fechaAsignacion = fechaAsignacion;
        this.segmentoBinarioActual = contribuyente.getSegmentoBinarioActual();
    }

    public Long getIdAsignacion() {
        return idAsignacion;
    }

    public Segmento getSegmento() {
        return segmento;
    }

    public DimContribuyente getContribuyente() {
        return contribuyente;
    }

    public LocalDateTime getFechaAsignacion() {
        return fechaAsignacion;
    }

    public String getSegmentoBinarioActual() {
        return segmentoBinarioActual;
    }
}
