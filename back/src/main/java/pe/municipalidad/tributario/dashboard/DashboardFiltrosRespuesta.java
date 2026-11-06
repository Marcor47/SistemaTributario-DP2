package pe.municipalidad.tributario.dashboard;

import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.ia.SegmentoRespuesta;

/* Valores con que el front arma los filtros del dashboard (HU011), tomados de la última carga publicada. */
public record DashboardFiltrosRespuesta(
        Long idLote,
        LocalDate fechaCorteCarga,
        List<Integer> anios,
        List<String> zonas,
        List<OpcionTributo> tributos,
        List<SegmentoRespuesta> segmentos) {

    public record OpcionTributo(String codigo, String descripcion) {
    }
}
