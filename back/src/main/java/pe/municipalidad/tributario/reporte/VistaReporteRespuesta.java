package pe.municipalidad.tributario.reporte;

import java.time.LocalDateTime;
import java.util.List;

/*
 * Representación de una vista guardada de reporte (HU007, RF-008).
 */
public record VistaReporteRespuesta(
        Long idVista,
        TipoReporte tipoReporte,
        String nombre,
        TipoGrafica tipoGrafica,
        FiltrosReporte filtros,
        List<String> columnasVisibles,
        String orden,
        LocalDateTime fechaCreacion) {
}

