package pe.municipalidad.tributario.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * Parámetro configurable sin modificar código: DIAS_RECORDATORIO (1 a 10, por defecto 5), HORA_ETL y
 * UMBRAL_DESVIO (10 % por defecto). Requisitos: RF-022, RF-038, RF-052.
 */
@Entity
@Table(name = "parametro_configuracion", schema = "seguridad")
public class ParametroConfiguracion {

    public static final String DIAS_RECORDATORIO = "DIAS_RECORDATORIO";
    public static final String HORA_ETL = "HORA_ETL";
    public static final String UMBRAL_DESVIO = "UMBRAL_DESVIO";

    @Id
    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor", nullable = false, length = 250)
    private String valor;

    /* Descripción y rango permitido. */
    @Column(name = "descripcion", nullable = false, length = 250)
    private String descripcion;

    protected ParametroConfiguracion() {
    }

    public void actualizar(String nuevoValor) {
        this.valor = nuevoValor;
    }

    public String getClave() {
        return clave;
    }

    public String getValor() {
        return valor;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
