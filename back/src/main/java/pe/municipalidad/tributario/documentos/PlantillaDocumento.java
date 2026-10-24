package pe.municipalidad.tributario.documentos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * Formulario o plantilla oficial del maestro compartido por todos los usuarios (RF-063, RF-046).
 * PlantillaDocumento 1 ◆ 1..* VersionPlantilla. codigo es la clave estable que usa la API de formularios.
 */
@Entity
@Table(name = "plantilla_documento", schema = "operativo")
public class PlantillaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla")
    private Long idPlantilla;

    @Column(name = "codigo", nullable = false, unique = true, length = 80)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPlantilla tipo;

    @Column(name = "descripcion", length = 400)
    private String descripcion;

    protected PlantillaDocumento() {
    }

    public PlantillaDocumento(String codigo, String nombre, TipoPlantilla tipo, String descripcion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.descripcion = descripcion;
    }

    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
    }

    public Long getIdPlantilla() {
        return idPlantilla;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPlantilla getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
