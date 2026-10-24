package pe.municipalidad.tributario.analitica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/* Dimensión de predio con su zona, para los reportes por zona (RF-024, RF-012). La carga el ETL. */
@Entity
@Immutable
@Table(name = "dim_predio", schema = "analitica")
public class DimPredio {

    @Id
    @Column(name = "id_predio")
    private Long idPredio;

    @Column(name = "seudonimo_predio", nullable = false, unique = true, length = 64)
    private String seudonimoPredio;

    @Column(name = "zona", nullable = false, length = 20)
    private String zona;

    @Column(name = "uso_predio", length = 60)
    private String usoPredio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_predio", nullable = false, length = 10)
    private TipoPredio tipoPredio;

    @Column(name = "base_imponible", precision = 14, scale = 2)
    private BigDecimal baseImponible;

    protected DimPredio() {
    }

    public Long getIdPredio() {
        return idPredio;
    }

    public String getSeudonimoPredio() {
        return seudonimoPredio;
    }

    public String getZona() {
        return zona;
    }

    public String getUsoPredio() {
        return usoPredio;
    }

    public TipoPredio getTipoPredio() {
        return tipoPredio;
    }

    public BigDecimal getBaseImponible() {
        return baseImponible;
    }
}
