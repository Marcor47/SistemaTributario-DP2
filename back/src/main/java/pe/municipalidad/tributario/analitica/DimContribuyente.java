package pe.municipalidad.tributario.analitica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.contribuyente.TipoPersona;

/*
 * Dimensión de contribuyente con identificador seudonimizado; no guarda DNI, RUC, nombres ni correos
 * (RF-011, RNF-018). La carga el ETL; la API solo la lee.
 */
@Entity
@Immutable
@Table(name = "dim_contribuyente", schema = "analitica")
public class DimContribuyente {

    @Id
    @Column(name = "seudonimo_contribuyente", length = 64)
    private String seudonimoContribuyente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false, length = 10)
    private TipoPersona tipoPersona;

    @Column(name = "regimen", length = 40)
    private String regimen;

    @Column(name = "condicion", length = 40)
    private String condicion;

    /* Regularidad de pago del año (RF-011). */
    @Column(name = "porcentaje_pago_a_tiempo", precision = 5, scale = 2)
    private BigDecimal porcentajePagoATiempo;

    /* Segmento actual de la Municipalidad: puntual, vecino excelente, moroso recurrente u otro. */
    @Column(name = "segmento_binario_actual", length = 30)
    private String segmentoBinarioActual;

    protected DimContribuyente() {
    }

    public String getSeudonimoContribuyente() {
        return seudonimoContribuyente;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public String getRegimen() {
        return regimen;
    }

    public String getCondicion() {
        return condicion;
    }

    public BigDecimal getPorcentajePagoATiempo() {
        return porcentajePagoATiempo;
    }

    public String getSegmentoBinarioActual() {
        return segmentoBinarioActual;
    }
}
