package pe.municipalidad.tributario.ia;

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
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/*
 * Registro inalterable de cada sugerencia o decisión automática de IA (RNF-014). Una revisión u override
 * humano posterior se registra como una nueva traza. ModeloIA 1 registra *.
 */
@Entity
@Immutable
@Table(name = "traza_ia", schema = "analitica")
public class TrazaIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_traza")
    private Long idTraza;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private ModeloIA modelo;

    @Column(name = "entrada", nullable = false, columnDefinition = "text")
    private String entrada;

    @Column(name = "salida", nullable = false, columnDefinition = "text")
    private String salida;

    @Column(name = "probabilidad", precision = 5, scale = 4)
    private BigDecimal probabilidad;

    @Column(name = "umbral_aplicado", precision = 5, scale = 4)
    private BigDecimal umbralAplicado;

    @Column(name = "evidencias", columnDefinition = "text")
    private String evidencias;

    @Column(name = "revision_humana", length = 400)
    private String revisionHumana;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected TrazaIA() {
    }

    public TrazaIA(ModeloIA modelo, String entrada, String salida, BigDecimal probabilidad,
            BigDecimal umbralAplicado, String evidencias, String revisionHumana, LocalDateTime fechaHora) {
        this.modelo = modelo;
        this.entrada = entrada;
        this.salida = salida;
        this.probabilidad = probabilidad;
        this.umbralAplicado = umbralAplicado;
        this.evidencias = evidencias;
        this.revisionHumana = revisionHumana;
        this.fechaHora = fechaHora;
    }

    public Long getIdTraza() {
        return idTraza;
    }

    public ModeloIA getModelo() {
        return modelo;
    }

    public String getEntrada() {
        return entrada;
    }

    public String getSalida() {
        return salida;
    }

    public BigDecimal getProbabilidad() {
        return probabilidad;
    }

    public BigDecimal getUmbralAplicado() {
        return umbralAplicado;
    }

    public String getEvidencias() {
        return evidencias;
    }

    public String getRevisionHumana() {
        return revisionHumana;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
