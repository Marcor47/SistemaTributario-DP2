package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/* Lo que muestra el dashboard por alerta (RF-052): indicador, valor, meta y desvío. */
public record AlertaDesvioRespuesta(
        Long idAlerta,
        String indicadorCodigo,
        String indicadorNombre,
        UnidadMedida unidadMedida,
        SentidoIndicador sentido,
        Long idMeta,
        int anio,
        int numeroPeriodo,
        String periodo,
        AlcanceIndicador alcance,
        String valorAlcance,
        BigDecimal valorObservado,
        BigDecimal valorMeta,
        BigDecimal desvioPorcentual,
        BigDecimal umbralDesvio,
        Long idLote,
        LocalDate fechaCorte,
        LocalDateTime fechaGeneracion) {

    public static AlertaDesvioRespuesta desde(AlertaDesvio alerta) {
        MetaIndicador meta = alerta.getMeta();
        Indicador indicador = meta.getIndicador();
        ValorIndicador valor = alerta.getValorIndicador();
        return new AlertaDesvioRespuesta(
                alerta.getIdAlerta(),
                indicador.getCodigo(),
                indicador.getNombre(),
                indicador.getUnidadMedida(),
                indicador.getSentido(),
                meta.getId(),
                meta.getAnio(),
                meta.getNumeroPeriodo(),
                meta.etiquetaPeriodo(),
                meta.getAlcance(),
                meta.getValorAlcance(),
                alerta.getValorObservado(),
                alerta.getValorMeta(),
                alerta.getDesvioPorcentual(),
                meta.getUmbralDesvio(),
                valor.getLote().getIdLote(),
                valor.getFechaCorte(),
                alerta.getFechaGeneracion());
    }
}
