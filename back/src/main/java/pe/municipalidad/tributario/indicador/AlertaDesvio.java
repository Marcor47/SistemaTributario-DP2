package pe.municipalidad.tributario.indicador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/*
 * Alerta del dashboard cuando una carga deja un indicador fuera de su meta más allá del umbral (RF-052).
 * Se desactiva cuando el indicador vuelve dentro del umbral. Meta 1 genera *; ValorIndicador 1 origina *.
 */
@Entity
@Table(name = "alerta_desvio", schema = "analitica")
public class AlertaDesvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long idAlerta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_meta", nullable = false)
    private MetaIndicador meta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_valor", nullable = false)
    private ValorIndicador valorIndicador;

    @Column(name = "valor_observado", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorObservado;

    @Column(name = "valor_meta", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorMeta;

    @Column(name = "desvio_porcentual", nullable = false, precision = 7, scale = 2)
    private BigDecimal desvioPorcentual;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected AlertaDesvio() {
    }

    /*
     * Compara el valor con la meta según el sentido del indicador y devuelve la alerta solo si el desvío
     * desfavorable supera el umbral de la meta.
     */
    public static Optional<AlertaDesvio> evaluar(MetaIndicador meta, ValorIndicador valor, LocalDateTime ahora) {
        if (meta.getValorMeta().signum() == 0) {
            return Optional.empty();
        }
        BigDecimal desvio = valor.getValor().subtract(meta.getValorMeta())
                .multiply(BigDecimal.valueOf(100))
                .divide(meta.getValorMeta(), 2, RoundingMode.HALF_UP);
        BigDecimal desvioDesfavorable = meta.getIndicador().getSentido() == SentidoIndicador.ASCENDENTE
                ? desvio.negate()
                : desvio;
        if (desvioDesfavorable.compareTo(meta.getUmbralDesvio()) <= 0) {
            return Optional.empty();
        }
        AlertaDesvio alerta = new AlertaDesvio();
        alerta.meta = meta;
        alerta.valorIndicador = valor;
        alerta.valorObservado = valor.getValor();
        alerta.valorMeta = meta.getValorMeta();
        alerta.desvioPorcentual = desvio;
        alerta.fechaGeneracion = ahora;
        alerta.activa = true;
        return Optional.of(alerta);
    }

    public void desactivar() {
        this.activa = false;
    }

    public Long getIdAlerta() {
        return idAlerta;
    }

    public MetaIndicador getMeta() {
        return meta;
    }

    public ValorIndicador getValorIndicador() {
        return valorIndicador;
    }

    public BigDecimal getValorObservado() {
        return valorObservado;
    }

    public BigDecimal getValorMeta() {
        return valorMeta;
    }

    public BigDecimal getDesvioPorcentual() {
        return desvioPorcentual;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public boolean isActiva() {
        return activa;
    }
}
