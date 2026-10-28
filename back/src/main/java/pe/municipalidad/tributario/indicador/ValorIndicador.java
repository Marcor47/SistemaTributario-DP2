package pe.municipalidad.tributario.indicador;

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
import java.time.LocalDate;
import pe.municipalidad.tributario.etl.LoteETL;

/*
 * Valor de un indicador calculado en una carga del ETL, a nivel total, por tributo, zona o segmento
 * (RF-009, RF-012). El periodo se guarda como año y número de periodo, igual que las metas, para
 * compararlos. Indicador 1 valores *; LoteETL 1 calcula *.
 */
@Entity
@Table(name = "valor_indicador", schema = "analitica")
public class ValorIndicador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valor")
    private Long idValor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicador_codigo", nullable = false)
    private Indicador indicador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lote", nullable = false)
    private LoteETL lote;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "numero_periodo", nullable = false)
    private int numeroPeriodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 10)
    private AlcanceIndicador alcance;

    /* Tributo, zona o segmento concreto; nulo si el alcance es TOTAL. */
    @Column(name = "valor_alcance", length = 40)
    private String valorAlcance;

    @Column(name = "valor", nullable = false, precision = 14, scale = 4)
    private BigDecimal valor;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    protected ValorIndicador() {
    }

    public ValorIndicador(Indicador indicador, LoteETL lote, int anio, int numeroPeriodo, AlcanceIndicador alcance,
            String valorAlcance, BigDecimal valor) {
        this.indicador = indicador;
        this.lote = lote;
        this.anio = anio;
        this.numeroPeriodo = numeroPeriodo;
        this.alcance = alcance;
        this.valorAlcance = alcance == AlcanceIndicador.TOTAL ? null : valorAlcance;
        this.valor = valor;
        this.fechaCorte = lote.getFechaCorte();
    }

    /* Recalcular el mismo lote reemplaza el valor; la fila se conserva porque sus alertas la referencian. */
    public void recalcular(BigDecimal nuevoValor) {
        this.valor = nuevoValor;
    }

    public String etiquetaPeriodo() {
        return indicador.getPeriodicidad().etiqueta(anio, numeroPeriodo);
    }

    public Long getIdValor() {
        return idValor;
    }

    public Indicador getIndicador() {
        return indicador;
    }

    public LoteETL getLote() {
        return lote;
    }

    public int getAnio() {
        return anio;
    }

    public int getNumeroPeriodo() {
        return numeroPeriodo;
    }

    public AlcanceIndicador getAlcance() {
        return alcance;
    }

    public String getValorAlcance() {
        return valorAlcance;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }
}
