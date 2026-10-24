package pe.municipalidad.tributario.reporte;

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
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Vista personalizada de un reporte del catálogo (tipo de gráfica, filtros, columnas y orden), guardada
 * por su dueño; solo él la abre (RF-008, RF-044, RNF-023). VistaReporte * de 1 Usuario.
 * La exportación la hace el servicio de reportes aplicando las mismas restricciones.
 */
@Entity
@Table(name = "vista_reporte", schema = "operativo")
public class VistaReporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vista")
    private Long idVista;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reporte", nullable = false, length = 20)
    private TipoReporte tipoReporte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_grafica", nullable = false, length = 10)
    private TipoGrafica tipoGrafica;

    /* Filtros y valores fijados, en JSON. */
    @Column(name = "filtros", columnDefinition = "text")
    private String filtros;

    /* Columnas visibles en su orden, en JSON. */
    @Column(name = "columnas_visibles", nullable = false, columnDefinition = "text")
    private String columnasVisibles;

    @Column(name = "orden", length = 250)
    private String orden;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected VistaReporte() {
    }

    public VistaReporte(TipoReporte tipoReporte, Usuario usuario, String nombre, TipoGrafica tipoGrafica,
            String filtros, String columnasVisibles, String orden, LocalDateTime fechaCreacion) {
        this.tipoReporte = tipoReporte;
        this.usuario = usuario;
        this.nombre = nombre;
        this.tipoGrafica = tipoGrafica;
        this.filtros = filtros;
        this.columnasVisibles = columnasVisibles;
        this.orden = orden;
        this.fechaCreacion = fechaCreacion;
    }

    public void actualizar(TipoGrafica tipoGrafica, String filtros, String columnasVisibles, String orden) {
        this.tipoGrafica = tipoGrafica;
        this.filtros = filtros;
        this.columnasVisibles = columnasVisibles;
        this.orden = orden;
    }

    public boolean perteneceA(Usuario otro) {
        return usuario.getIdUsuario().equals(otro.getIdUsuario());
    }

    public Long getIdVista() {
        return idVista;
    }

    public TipoReporte getTipoReporte() {
        return tipoReporte;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoGrafica getTipoGrafica() {
        return tipoGrafica;
    }

    public String getFiltros() {
        return filtros;
    }

    public String getColumnasVisibles() {
        return columnasVisibles;
    }

    public String getOrden() {
        return orden;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
