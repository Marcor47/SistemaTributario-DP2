package pe.municipalidad.tributario.reporte;

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
import pe.municipalidad.tributario.seguridad.Rol;

/*
 * Regla de acceso que limita un reporte completo o su información por segmento o zona, según el rol.
 * Se aplica al visualizar y al exportar (RF-060). RestriccionReporte * aplica a 1 Rol.
 */
@Entity
@Table(name = "restriccion_reporte", schema = "operativo")
public class RestriccionReporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_restriccion")
    private Long idRestriccion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reporte", nullable = false, length = 20)
    private TipoReporte tipoReporte;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 10)
    private DimensionRestringida dimension;

    /* Segmento o zona que el rol puede ver; nulo cuando la restricción es sobre el reporte completo. */
    @Column(name = "valor_permitido", length = 80)
    private String valorPermitido;

    @Column(name = "permitido", nullable = false)
    private boolean permitido;

    protected RestriccionReporte() {
    }

    public RestriccionReporte(Rol rol, TipoReporte tipoReporte, DimensionRestringida dimension,
            String valorPermitido, boolean permitido) {
        this.rol = rol;
        this.tipoReporte = tipoReporte;
        this.dimension = dimension;
        this.valorPermitido = dimension == DimensionRestringida.REPORTE ? null : valorPermitido;
        this.permitido = permitido;
    }

    /* aplicarA(consulta): indica si el valor de la dimensión (zona o segmento de una fila) puede verse. */
    public boolean permite(TipoReporte reporte, DimensionRestringida dimensionFila, String valorFila) {
        if (reporte != tipoReporte) {
            return true;
        }
        if (dimension == DimensionRestringida.REPORTE) {
            return permitido;
        }
        if (dimension != dimensionFila || !valorPermitido.equals(valorFila)) {
            return true;
        }
        return permitido;
    }

    public Long getIdRestriccion() {
        return idRestriccion;
    }

    public Rol getRol() {
        return rol;
    }

    public TipoReporte getTipoReporte() {
        return tipoReporte;
    }

    public DimensionRestringida getDimension() {
        return dimension;
    }

    public String getValorPermitido() {
        return valorPermitido;
    }

    public boolean isPermitido() {
        return permitido;
    }
}
