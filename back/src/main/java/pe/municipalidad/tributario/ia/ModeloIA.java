package pe.municipalidad.tributario.ia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/*
 * Modelo de IA registrado, ejecutado de forma local, cuyo hash se verifica antes de cargarlo
 * (RF-043, RNF-013, RNF-037). La carga del artefacto (cargar) la hace el servicio de IA en Python.
 */
@Entity
@Table(name = "modelo_ia", schema = "analitica")
public class ModeloIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo")
    private Long idModelo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoModeloIA tipo;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /* SHA-256 publicado por la fuente oficial. */
    @Column(name = "hash_integridad", nullable = false, length = 64)
    private String hashIntegridad;

    /* Formato que no ejecuta código al cargarse (ONNX, GGUF, safetensors). */
    @Column(name = "formato", nullable = false, length = 20)
    private String formato;

    @Column(name = "ejecucion_local", nullable = false)
    private boolean ejecucionLocal;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected ModeloIA() {
    }

    public ModeloIA(String nombre, TipoModeloIA tipo, String version, String hashIntegridad, String formato,
            LocalDateTime fechaRegistro) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.version = version;
        this.hashIntegridad = hashIntegridad;
        this.formato = formato;
        this.ejecucionLocal = true;
        this.fechaRegistro = fechaRegistro;
    }

    /* Compara el SHA-256 del artefacto descargado con el registrado. */
    public boolean verificarHash(String hashCalculado) {
        return hashIntegridad.equalsIgnoreCase(hashCalculado);
    }

    public Long getIdModelo() {
        return idModelo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoModeloIA getTipo() {
        return tipo;
    }

    public String getVersion() {
        return version;
    }

    public String getHashIntegridad() {
        return hashIntegridad;
    }

    public String getFormato() {
        return formato;
    }

    public boolean isEjecucionLocal() {
        return ejecucionLocal;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
