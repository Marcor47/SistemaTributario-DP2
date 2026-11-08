package pe.municipalidad.tributario.reporte;

import java.util.List;

/*
 * HU032 (RF-008, RF-044): opciones con que el usuario puede personalizar la visualización de un reporte.
 * Sin el permiso REPORTES:PERSONALIZAR, tiposGrafica solo incluye TABLA. En filtros, valores lista las opciones de un
 * filtro de tipo LISTA (las zonas ya vienen filtradas por las restricciones del rol); minimo y maximo acotan los
 * filtros ANIO, ENTERO y MONTO.
 */
public record OpcionesVisualizacionRespuesta(
        TipoReporte tipoReporte,
        boolean puedePersonalizar,
        List<TipoGrafica> tiposGrafica,
        List<Serie> series,
        List<Filtro> filtros,
        PorDefecto porDefecto) {

    public record Serie(String id, String etiqueta, FormatoSerie formato) {
    }

    public enum FormatoSerie {
        MONTO,
        CANTIDAD
    }

    public record Filtro(String nombre, TipoFiltro tipo, List<String> valores, Integer minimo, Integer maximo) {
    }

    public enum TipoFiltro {
        ANIO,
        LISTA,
        TEXTO,
        MONTO,
        ENTERO
    }

    public record PorDefecto(TipoGrafica tipoGrafica, String serie) {
    }
}
