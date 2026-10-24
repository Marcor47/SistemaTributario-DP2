package pe.municipalidad.tributario.reclamo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Área de la Municipalidad a la que el gestor deriva un reclamo (RF-064). Catálogo pendiente de validar. */
@Entity
@Table(name = "area_municipal", schema = "operativo")
public class AreaMunicipal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_area")
    private Long idArea;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected AreaMunicipal() {
    }

    public AreaMunicipal(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = true;
    }

    public Long getIdArea() {
        return idArea;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActiva() {
        return activa;
    }
}
