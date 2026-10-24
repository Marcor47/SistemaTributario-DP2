package pe.municipalidad.tributario.etl;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.municipalidad.tributario.common.ReglaNegocioException;

/*
 * Ejecución del ETL con su fuente y fecha de corte (RF-038, RF-041). La escribe el orquestador en Python;
 * Java la lee para la auditoría de lotes. Un lote rechazado no afecta los datos ya publicados.
 * LoteETL 1 ◆ * ResultadoRegla; 1 genera * CasoHomologacion; 1 carga * hechos; 1 calcula * ValorIndicador.
 */
@Entity
@Table(name = "lote_etl", schema = "analitica")
public class LoteETL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Long idLote;

    /* Procedimiento de SAMNET de origen. */
    @Column(name = "fuente", nullable = false, length = 80)
    private String fuente;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ejecucion", nullable = false, length = 10)
    private TipoEjecucion tipoEjecucion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoLote estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "registros_leidos", nullable = false)
    private int registrosLeidos;

    @Column(name = "registros_aceptados", nullable = false)
    private int registrosAceptados;

    @Column(name = "registros_rechazados", nullable = false)
    private int registrosRechazados;

    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultadoRegla> resultados = new ArrayList<>();

    protected LoteETL() {
    }

    /* ejecutar(): abre el lote en curso. */
    public LoteETL(String fuente, LocalDate fechaCorte, TipoEjecucion tipoEjecucion, LocalDateTime fechaInicio) {
        this.fuente = fuente;
        this.fechaCorte = fechaCorte;
        this.tipoEjecucion = tipoEjecucion;
        this.fechaInicio = fechaInicio;
        this.estado = EstadoLote.EN_CURSO;
    }

    public void registrarResultado(ResultadoRegla resultado) {
        resultado.asignarA(this);
        resultados.add(resultado);
    }

    /* Se publica solo si ninguna regla falla. */
    public void publicar(int leidos, int aceptados, LocalDateTime ahora) {
        if (resultados.stream().anyMatch(r -> r.getResultado() == ResultadoCalidad.FALLA)) {
            throw new ReglaNegocioException("LOTE_CON_FALLAS", "Un lote con reglas en FALLA no se publica.");
        }
        cerrar(EstadoLote.PUBLICADO, leidos, aceptados, ahora);
    }

    public void rechazar(int leidos, int aceptados, LocalDateTime ahora) {
        cerrar(EstadoLote.RECHAZADO, leidos, aceptados, ahora);
    }

    private void cerrar(EstadoLote nuevoEstado, int leidos, int aceptados, LocalDateTime ahora) {
        if (estado != EstadoLote.EN_CURSO) {
            throw new ReglaNegocioException("LOTE_CERRADO", "El lote ya fue " + estado + ".");
        }
        this.registrosLeidos = leidos;
        this.registrosAceptados = aceptados;
        this.registrosRechazados = leidos - aceptados;
        this.fechaFin = ahora;
        this.estado = nuevoEstado;
    }

    public Long getIdLote() {
        return idLote;
    }

    public String getFuente() {
        return fuente;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public TipoEjecucion getTipoEjecucion() {
        return tipoEjecucion;
    }

    public EstadoLote getEstado() {
        return estado;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public int getRegistrosLeidos() {
        return registrosLeidos;
    }

    public int getRegistrosAceptados() {
        return registrosAceptados;
    }

    public int getRegistrosRechazados() {
        return registrosRechazados;
    }

    public List<ResultadoRegla> getResultados() {
        return List.copyOf(resultados);
    }
}
