package pe.municipalidad.tributario.reporte;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudesReporteRespuesta(
        long total,
        long usuariosDistintos,
        List<FrecuenciaReporte> frecuencias,
        List<SolicitudReporteItem> solicitudes,
        int pagina,
        int tamanio,
        int totalPaginas) {

    public record FrecuenciaReporte(TipoReporte tipo, String titulo, long cantidad) {
    }

    public record SolicitudReporteItem(
            Long idSolicitud,
            String nombreUsuario,
            String nombreCompleto,
            TipoReporte tipoReporte,
            String tituloReporte,
            LocalDateTime fechaHora,
            String operacion,
            String filtrosAplicados) {
    }
}
