package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaIndicadorRespuesta(
        Long id,
        String indicadorCodigo,
        String indicadorNombre,
        UnidadMedida unidadMedida,
        SentidoIndicador sentido,
        Periodicidad periodicidad,
        int anio,
        int numeroPeriodo,
        String periodo,
        LocalDate fechaFinPeriodo,
        BigDecimal valorMeta,
        String observacion,
        boolean editable,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaActualizacion) {

    public static MetaIndicadorRespuesta desde(MetaIndicador meta, LocalDate hoy) {
        Indicador indicador = meta.getIndicador();
        return new MetaIndicadorRespuesta(
                meta.getId(),
                indicador.getCodigo(),
                indicador.getNombre(),
                indicador.getUnidadMedida(),
                indicador.getSentido(),
                indicador.getPeriodicidad(),
                meta.getAnio(),
                meta.getNumeroPeriodo(),
                meta.etiquetaPeriodo(),
                meta.fechaFinPeriodo(),
                meta.getValorMeta(),
                meta.getObservacion(),
                !meta.fechaFinPeriodo().isBefore(hoy),
                meta.getFechaRegistro(),
                meta.getFechaActualizacion());
    }
}
