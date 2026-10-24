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
import java.time.LocalDateTime;
import pe.municipalidad.tributario.seguridad.Usuario;

@Entity
@Table(name = "meta_indicador", schema = "analitica")
public class MetaIndicador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicador_codigo", nullable = false)
    private Indicador indicador;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "numero_periodo", nullable = false)
    private int numeroPeriodo;

    @Column(name = "valor_meta", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorMeta;

    @Column(name = "observacion", length = 250)
    private String observacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /* Meta del modelo (RF-010, RF-052): alcance, umbral de desvío y usuario que la registró. */
    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 10)
    private AlcanceIndicador alcance = AlcanceIndicador.TOTAL;

    /* Tributo, zona o segmento concreto; nulo si el alcance es TOTAL. */
    @Column(name = "valor_alcance", length = 40)
    private String valorAlcance;

    @Column(name = "umbral_desvio", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralDesvio = UMBRAL_DESVIO_POR_DEFECTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_registro")
    private Usuario usuarioRegistro;

    public static final BigDecimal UMBRAL_DESVIO_POR_DEFECTO = BigDecimal.TEN;

    protected MetaIndicador() {
    }

    public MetaIndicador(Indicador indicador, int anio, int numeroPeriodo, BigDecimal valorMeta,
            String observacion, LocalDateTime fechaRegistro) {
        this.indicador = indicador;
        this.anio = anio;
        this.numeroPeriodo = numeroPeriodo;
        this.valorMeta = valorMeta;
        this.observacion = observacion;
        this.fechaRegistro = fechaRegistro;
        this.fechaActualizacion = fechaRegistro;
    }

    /* Acota la meta a un tributo, zona o segmento; TOTAL la deja sin acotar. */
    public void acotar(AlcanceIndicador nuevoAlcance, String nuevoValorAlcance) {
        this.alcance = nuevoAlcance;
        this.valorAlcance = nuevoAlcance == AlcanceIndicador.TOTAL ? null : nuevoValorAlcance;
    }

    public void definirUmbralDesvio(BigDecimal nuevoUmbral) {
        this.umbralDesvio = nuevoUmbral;
    }

    public void registradaPor(Usuario usuario) {
        this.usuarioRegistro = usuario;
    }

    public void actualizar(BigDecimal nuevoValorMeta, String nuevaObservacion, LocalDateTime fechaCambio) {
        this.valorMeta = nuevoValorMeta;
        this.observacion = nuevaObservacion;
        this.fechaActualizacion = fechaCambio;
    }

    public LocalDate fechaFinPeriodo() {
        return indicador.getPeriodicidad().fechaFin(anio, numeroPeriodo);
    }

    public String etiquetaPeriodo() {
        return indicador.getPeriodicidad().etiqueta(anio, numeroPeriodo);
    }

    public Long getId() {
        return id;
    }

    public Indicador getIndicador() {
        return indicador;
    }

    public int getAnio() {
        return anio;
    }

    public int getNumeroPeriodo() {
        return numeroPeriodo;
    }

    public BigDecimal getValorMeta() {
        return valorMeta;
    }

    public String getObservacion() {
        return observacion;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public AlcanceIndicador getAlcance() {
        return alcance;
    }

    public String getValorAlcance() {
        return valorAlcance;
    }

    public BigDecimal getUmbralDesvio() {
        return umbralDesvio;
    }

    public Usuario getUsuarioRegistro() {
        return usuarioRegistro;
    }
}
