package pe.municipalidad.tributario.indicador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * Catálogo de indicadores: cada uno con definición, fórmula, fuente y periodicidad,
 * como exige el documento del tema del proyecto.
 */
@Entity
@Table(name = "indicador", schema = "analitica")
public class Indicador {

    @Id
    @Column(name = "codigo", length = 40)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "definicion", nullable = false, length = 400)
    private String definicion;

    @Column(name = "formula", nullable = false, length = 250)
    private String formula;

    @Column(name = "fuente", nullable = false, length = 150)
    private String fuente;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 12)
    private UnidadMedida unidadMedida;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidad", nullable = false, length = 12)
    private Periodicidad periodicidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentido", nullable = false, length = 12)
    private SentidoIndicador sentido;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Indicador() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDefinicion() {
        return definicion;
    }

    public String getFormula() {
        return formula;
    }

    public String getFuente() {
        return fuente;
    }

    public UnidadMedida getUnidadMedida() {
        return unidadMedida;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public SentidoIndicador getSentido() {
        return sentido;
    }

    public boolean isActivo() {
        return activo;
    }
}
