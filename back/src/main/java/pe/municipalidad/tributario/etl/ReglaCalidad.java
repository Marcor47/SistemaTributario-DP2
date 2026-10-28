package pe.municipalidad.tributario.etl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * Regla de calidad basada en ISO/IEC 25012 (RF-038): R1 documento y código no nulos; R2 DNI de 8 y RUC de
 * 11 dígitos; R3 predio con contribuyente o marcado; R4 sin claves repetidas; R5 fecha de corte esperada.
 */
@Entity
@Table(name = "regla_calidad", schema = "analitica")
public class ReglaCalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regla")
    private Long idRegla;

    @Column(name = "codigo", nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 250)
    private String descripcion;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected ReglaCalidad() {
    }

    public Long getIdRegla() {
        return idRegla;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActiva() {
        return activa;
    }
}
