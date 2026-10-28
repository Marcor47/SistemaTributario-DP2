package pe.municipalidad.tributario.cobranza;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * Ingreso de una obligación a una etapa de cobranza, tal como lo registra SAMNET (RF-014).
 * La etapa actual es la de fecha de inicio más reciente.
 */
public record SeguimientoCobranza(
        Long id,
        Long obligacionId,
        EtapaCobranza etapa,
        LocalDate fechaInicio,
        String documentoReferencia,
        String observacion,
        LocalDateTime fechaRegistro) {
}
