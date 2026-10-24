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
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Registro de cada solicitud de generación de un reporte, para saber quién lo pide y con qué frecuencia
 * (RF-059). El conteo por reporte (contarPorReporte) lo resuelve SolicitudReporteRepository.
 */
@Entity
@Immutable
@Table(name = "solicitud_reporte", schema = "operativo")
public class SolicitudReporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reporte", nullable = false, length = 20)
    private TipoReporte tipoReporte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "filtros_aplicados", columnDefinition = "text")
    private String filtrosAplicados;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato_exportacion", nullable = false, length = 10)
    private FormatoExportacion formatoExportacion;

    protected SolicitudReporte() {
    }

    public SolicitudReporte(TipoReporte tipoReporte, Usuario usuario, LocalDateTime fechaHora,
            String filtrosAplicados, FormatoExportacion formatoExportacion) {
        this.tipoReporte = tipoReporte;
        this.usuario = usuario;
        this.fechaHora = fechaHora;
        this.filtrosAplicados = filtrosAplicados;
        this.formatoExportacion = formatoExportacion;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public TipoReporte getTipoReporte() {
        return tipoReporte;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getFiltrosAplicados() {
        return filtrosAplicados;
    }

    public FormatoExportacion getFormatoExportacion() {
        return formatoExportacion;
    }
}
