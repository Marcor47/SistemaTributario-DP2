package pe.municipalidad.tributario.reporte;

/*
 * Resultado de abrir una vista guardada de reporte con sus datos generados (HU007, RF-008).
 */
public record VistaEjecutadaRespuesta<T>(
        VistaReporteRespuesta vista,
        ReporteRespuesta<T> reporte) {
}

