package pe.municipalidad.tributario.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.indicador.AlcanceIndicador;
import pe.municipalidad.tributario.indicador.SentidoIndicador;
import pe.municipalidad.tributario.indicador.UnidadMedida;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Dashboard ejecutivo de Rentas (HU011). Los porcentajes van de 0 a 100 con 4 decimales; son nulos cuando no hay
 * monto emitido, porque el indicador no está definido.
 */
public record DashboardRespuesta(
        Long idLote,
        LocalDate fechaCorteCarga,
        LocalDate fechaCorte,
        int anio,
        int numeroPeriodo,
        String periodo,
        FiltrosAplicados filtros,
        Resumen resumen,
        List<AvanceMeta> avanceMetas,
        List<PorTributo> porTributo,
        List<PorZona> porZona,
        List<PorSegmento> porSegmento) {

    public record FiltrosAplicados(String zona, Tributo tributo, Long idSegmento, String segmento) {
    }

    public record Resumen(BigDecimal montoEmitido, BigDecimal montoRecaudado, BigDecimal porcentajeRecaudacion,
            BigDecimal montoVencidoNoPagado, BigDecimal indiceMorosidad) {
    }

    /*
     * Avance frente a la meta del periodo. Solo se compara una meta cuyo alcance coincide con el filtro aplicado:
     * sin filtros, las de alcance TOTAL; con un solo filtro, las de ese tributo, zona o segmento.
     */
    public record AvanceMeta(String indicadorCodigo, String indicador, UnidadMedida unidadMedida,
            SentidoIndicador sentido, AlcanceIndicador alcance, String valorAlcance, BigDecimal valorMeta,
            BigDecimal valorObservado, BigDecimal porcentajeAvance, Boolean cumpleMeta) {
    }

    public record PorTributo(Tributo tributo, String descripcion, BigDecimal montoEmitido,
            BigDecimal montoRecaudado, BigDecimal porcentajeRecaudacion, BigDecimal participacionRecaudacion,
            BigDecimal indiceMorosidad) {
    }

    public record PorZona(String zona, BigDecimal montoEmitido, BigDecimal montoRecaudado,
            BigDecimal porcentajeRecaudacion, BigDecimal indiceMorosidad) {
    }

    public record PorSegmento(Long idSegmento, String nombre, long contribuyentes, BigDecimal montoEmitido,
            BigDecimal montoRecaudado, BigDecimal montoVencidoNoPagado, BigDecimal porcentajeRecaudacion,
            BigDecimal indiceMorosidad) {
    }
}
