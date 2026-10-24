package pe.municipalidad.tributario.cobranza;

import java.time.LocalDate;

/*
 * Un tramo del historial de cobranza. "registrada" es false solo para la mora
 * temprana implícita: toda deuda vencida está en esa etapa desde el día siguiente
 * a su vencimiento, aunque nadie la haya registrado.
 */
public record EtapaRespuesta(
        EtapaCobranza etapa,
        String nombre,
        String descripcion,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String documentoReferencia,
        String observacion,
        boolean registrada) {
}
