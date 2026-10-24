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
 * Resumen de hasta 300 palabras generado con un modelo de lenguaje local, basado solo en indicadores
 * calculados (RF-023). El usuario lo revisa antes de descargarlo. ModeloIA 1 genera *;
 * ResumenEjecutivo * se basa en 1..* ValorIndicador (indicadoresUsados).
 */
@Entity
@Table(name = "resumen_ejecutivo", schema = "analitica")
public class ResumenEjecutivo {

    public static final int MAXIMO_PALABRAS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resumen")
    private Long idResumen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private ModeloIA modelo;

    @Column(name = "periodo", nullable = false, length = 20)
    private String periodo;

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    /* Indicadores y su id de valor de los que sale cada cifra, en JSON. */
    @Column(name = "indicadores_usados", nullable = false, columnDefinition = "text")
    private String indicadoresUsados;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoResumen estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisor")
    private Usuario revisor;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    protected ResumenEjecutivo() {
    }

    /* generar(periodo): el texto lo produce el servicio de IA; aquí se valida y se registra. */
    public ResumenEjecutivo(ModeloIA modelo, String periodo, String texto, String indicadoresUsados,
            LocalDateTime fechaGeneracion) {
        if (texto.isBlank() || texto.trim().split("\\s+").length > MAXIMO_PALABRAS) {
            throw new ReglaNegocioException("RESUMEN_INVALIDO",
                    "El resumen debe tener entre 1 y " + MAXIMO_PALABRAS + " palabras.");
        }
        this.modelo = modelo;
        this.periodo = periodo;
        this.texto = texto;
        this.indicadoresUsados = indicadoresUsados;
        this.fechaGeneracion = fechaGeneracion;
        this.estado = EstadoResumen.GENERADO;
    }

    public void revisar(Usuario revisor) {
        this.revisor = revisor;
        this.estado = EstadoResumen.REVISADO;
    }

    /* Solo se descarga después de la revisión humana. */
    public String descargar() {
        if (estado != EstadoResumen.REVISADO) {
            throw new ReglaNegocioException("RESUMEN_SIN_REVISAR", "El resumen debe revisarse antes de descargarlo.");
        }
        return texto;
    }

    public Long getIdResumen() {
        return idResumen;
    }

    public ModeloIA getModelo() {
        return modelo;
    }

    public String getPeriodo() {
        return periodo;
    }

    public String getTexto() {
        return texto;
    }

    public String getIndicadoresUsados() {
        return indicadoresUsados;
    }

    public EstadoResumen getEstado() {
        return estado;
    }

    public Usuario getRevisor() {
        return revisor;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }
}
