package pe.municipalidad.tributario.reporte;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import pe.municipalidad.tributario.reporte.OpcionesVisualizacionRespuesta.FormatoSerie;

/*
 * HU032 (RF-008, RF-044): conjunto predefinido de opciones con que la Gerencia de Rentas puede personalizar la
 * visualización de cada reporte del catálogo, sin intervención de TI. Los ids de las series coinciden con los que usa
 * frontweb (Reportes.jsx, ReportePricos.jsx y ReporteBuenosPagadores.jsx) y con los guardados en vista_reporte.series.
 */
final class OpcionesVisualizacion {

    /* Año más antiguo admitido en los filtros; el más reciente es el año de la fecha actual. */
    static final int ANIO_MINIMO = 2000;
    static final int ANIO_MINIMO_RANGO_MOROSOS = 1900;
    static final int LIMITE_PRICOS_MINIMO = 1;
    static final int LIMITE_PRICOS_MAXIMO = 200;

    record Serie(String id, String etiqueta, FormatoSerie formato) {
    }

    record Configuracion(List<TipoGrafica> tiposGrafica, List<Serie> series, List<String> filtros) {

        String seriePorDefecto() {
            return series.getFirst().id();
        }

        boolean admiteGrafica(TipoGrafica tipoGrafica) {
            return tiposGrafica.contains(tipoGrafica);
        }

        Optional<Serie> serie(String id) {
            return series.stream().filter(serie -> serie.id().equals(id)).findFirst();
        }
    }

    private static final Map<TipoReporte, Configuracion> CONFIGURACIONES = Map.of(
            TipoReporte.MOROSOS, new Configuracion(
                    List.of(TipoGrafica.TABLA, TipoGrafica.BARRAS, TipoGrafica.LINEAS, TipoGrafica.CIRCULAR),
                    List.of(
                            new Serie("deudaVencida", "Deuda vencida", FormatoSerie.MONTO),
                            new Serie("montoSinBeneficio", "Deuda sin beneficio", FormatoSerie.MONTO),
                            new Serie("montoConBeneficio", "Deuda con beneficio", FormatoSerie.MONTO)),
                    List.of("anioDesde", "anioHasta", "zona", "tributo", "regimen", "tipoDocumento", "etapa",
                            "beneficio", "montoMinimo")),
            TipoReporte.PRICOS, new Configuracion(
                    List.of(TipoGrafica.TABLA, TipoGrafica.BARRAS, TipoGrafica.CIRCULAR),
                    List.of(
                            new Serie("baseImponible", "Base imponible", FormatoSerie.MONTO),
                            new Serie("emisionTotal", "Emisión total", FormatoSerie.MONTO),
                            new Serie("deudaAnterior", "Deuda de años anteriores", FormatoSerie.MONTO),
                            new Serie("deudaActual", "Deuda con el año de corte", FormatoSerie.MONTO)),
                    List.of("anio", "zona", "tributo", "montoMinimo", "criterio", "limite")),
            TipoReporte.BUENOS_PAGADORES, new Configuracion(
                    List.of(TipoGrafica.TABLA, TipoGrafica.BARRAS, TipoGrafica.CIRCULAR),
                    List.of(
                            new Serie("totalPagado", "Total pagado", FormatoSerie.MONTO),
                            new Serie("montoPagadoPredial", "Pagado por Impuesto Predial", FormatoSerie.MONTO),
                            new Serie("montoPagadoArbitrios", "Pagado por Arbitrios", FormatoSerie.MONTO),
                            new Serie("cuotasPagadasATiempo", "Cuotas pagadas a tiempo", FormatoSerie.CANTIDAD)),
                    List.of("periodo", "anio", "zona", "tributo", "montoMinimo")));

    private OpcionesVisualizacion() {
    }

    static Configuracion de(TipoReporte tipo) {
        return CONFIGURACIONES.get(tipo);
    }
}
