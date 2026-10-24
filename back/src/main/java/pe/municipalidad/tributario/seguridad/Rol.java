package pe.municipalidad.tributario.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;

/* Perfil funcional que agrupa los módulos y acciones permitidos (RF-047, RNF-023). */
@Entity
@Table(name = "rol", schema = "seguridad")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long idRol;

    @Enumerated(EnumType.STRING)
    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private NombreRol nombre;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    /* Permisos MODULO:ACCION separados por comas, p. ej. REPORTES:CONSULTAR,REPORTES:EXPORTAR. */
    @Column(name = "permisos", nullable = false, columnDefinition = "text")
    private String permisos;

    protected Rol() {
    }

    public Rol(NombreRol nombre, String descripcion, String permisos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.permisos = permisos;
    }

    public boolean tienePermiso(String modulo, String accion) {
        String buscado = modulo + ":" + accion;
        return Arrays.stream(permisos.split(",")).map(String::trim).anyMatch(buscado::equalsIgnoreCase);
    }

    public Long getIdRol() {
        return idRol;
    }

    public NombreRol getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPermisos() {
        return permisos;
    }
}
