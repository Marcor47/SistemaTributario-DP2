package pe.municipalidad.tributario.reporte;

import java.util.List;

/* Un reporte del catálogo que el rol del usuario puede generar, con los filtros que acepta (HU006). */
public record ReporteCatalogoRespuesta(
        TipoReporte tipo,
        String titulo,
        String descripcion,
        List<String> filtros) {
}
