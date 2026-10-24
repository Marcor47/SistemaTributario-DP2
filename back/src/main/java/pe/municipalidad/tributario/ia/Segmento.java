package pe.municipalidad.tributario.ia;

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
import java.time.LocalDateTime;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Grupo de contribuyentes con comportamiento de pago similar, descubierto por el modelo no supervisado.
 * Nace en borrador y solo se publica en el dashboard tras la aprobación de la jefatura (RF-043).
 * ModeloIA 1 produce *; Segmento 1 ◆ * AsignacionSegmento.
 */
@Entity
@Table(name = "segmento", schema = "analitica")
public class Segmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_segmento")
    private Long idSegmento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private ModeloIA modelo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 400)
    private String descripcion;

    @Column(name = "tamano", nullable = false)
    private int tamano;

    /* Variables que lo caracterizan, en JSON. */
    @Column(name = "variables_caracteristicas", nullable = false, columnDefinition = "text")
    private String variablesCaracteristicas;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoSegmento estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aprobador")
    private Usuario aprobador;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    protected Segmento() {
    }

    public Segmento(ModeloIA modelo, String nombre, String descripcion, int tamano, String variablesCaracteristicas) {
        this.modelo = modelo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tamano = tamano;
        this.variablesCaracteristicas = variablesCaracteristicas;
        this.estado = EstadoSegmento.BORRADOR;
    }

    public void aprobar(Usuario jefatura, LocalDateTime ahora) {
        if (estado != EstadoSegmento.BORRADOR) {
            throw new ReglaNegocioException("SEGMENTO_NO_BORRADOR", "Solo se aprueba un segmento en borrador.");
        }
        this.aprobador = jefatura;
        this.fechaAprobacion = ahora;
        this.estado = EstadoSegmento.APROBADO;
    }

    public void publicar() {
        if (estado != EstadoSegmento.APROBADO) {
            throw new ReglaNegocioException("SEGMENTO_NO_APROBADO",
                    "El segmento debe estar aprobado por la jefatura antes de publicarse.");
        }
        this.estado = EstadoSegmento.PUBLICADO;
    }

    public Long getIdSegmento() {
        return idSegmento;
    }

    public ModeloIA getModelo() {
        return modelo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getTamano() {
        return tamano;
    }

    public String getVariablesCaracteristicas() {
        return variablesCaracteristicas;
    }

    public EstadoSegmento getEstado() {
        return estado;
    }

    public Usuario getAprobador() {
        return aprobador;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }
}
