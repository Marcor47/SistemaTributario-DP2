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

/* Resultado de cada regla de calidad en un lote (RF-041). ReglaCalidad 1 evalúa * ResultadoRegla. */
@Entity
@Table(name = "resultado_regla", schema = "analitica")
public class ResultadoRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultado")
    private Long idResultado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lote", nullable = false)
    private LoteETL lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_regla", nullable = false)
    private ReglaCalidad regla;

    @Column(name = "registros_evaluados", nullable = false)
    private int registrosEvaluados;

    @Column(name = "registros_con_error", nullable = false)
    private int registrosConError;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 10)
    private ResultadoCalidad resultado;

    @Column(name = "detalle", length = 400)
    private String detalle;

    protected ResultadoRegla() {
    }

    public ResultadoRegla(ReglaCalidad regla, int registrosEvaluados, int registrosConError,
            ResultadoCalidad resultado, String detalle) {
        this.regla = regla;
        this.registrosEvaluados = registrosEvaluados;
        this.registrosConError = registrosConError;
        this.resultado = resultado;
        this.detalle = detalle;
    }

    void asignarA(LoteETL lote) {
        this.lote = lote;
    }

    public Long getIdResultado() {
        return idResultado;
    }

    public LoteETL getLote() {
        return lote;
    }

    public ReglaCalidad getRegla() {
        return regla;
    }

    public int getRegistrosEvaluados() {
        return registrosEvaluados;
    }

    public int getRegistrosConError() {
        return registrosConError;
    }

    public ResultadoCalidad getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }
}
