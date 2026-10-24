package pe.municipalidad.tributario.ia;

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
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.analitica.DimContribuyente;

/*
 * Clasificación del riesgo de mora de un contribuyente seudonimizado, de apoyo a la priorización de
 * cobranza (RF-035, prioridad baja). La calcula el servicio de IA. ModeloIA 1 calcula *.
 */
@Entity
@Immutable
@Table(name = "clasificacion_riesgo", schema = "analitica")
public class ClasificacionRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_clasificacion")
    private Long idClasificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private ModeloIA modelo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seudonimo_contribuyente", nullable = false)
    private DimContribuyente contribuyente;

    @Column(name = "probabilidad_mora", nullable = false, precision = 5, scale = 4)
    private BigDecimal probabilidadMora;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_riesgo", nullable = false, length = 5)
    private CategoriaRiesgo categoriaRiesgo;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    protected ClasificacionRiesgo() {
    }

    public Long getIdClasificacion() {
        return idClasificacion;
    }

    public ModeloIA getModelo() {
        return modelo;
    }

    public DimContribuyente getContribuyente() {
        return contribuyente;
    }

    public BigDecimal getProbabilidadMora() {
        return probabilidadMora;
    }

    public CategoriaRiesgo getCategoriaRiesgo() {
        return categoriaRiesgo;
    }

    public LocalDateTime getFechaCalculo() {
        return fechaCalculo;
    }
}
