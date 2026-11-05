package pe.municipalidad.tributario.regularidad;

import java.math.BigDecimal;

/**
 * HU010 - DTO de respuesta para el calculo de regularidad de pago del contribuyente.
 *
 * <p>Expone el porcentaje de cuotas pagadas a tiempo en un anio determinado,
 * utilizado como insumo clave para la segmentacion del contribuyente y su ficha.</p>
 *
 * <p>El porcentaje se calcula sobre las cuotas exigibles (ya vencidas): una cuota que aún no vence no cuenta en
 * contra. cuotasPendientesOVencidas son las exigibles que siguen impagas.</p>
 */
public record RegularidadPagoRespuesta(
        String codigoContribuyente,
        String nombreContribuyente,
        int anio,
        int totalCuotasEmitidas,
        int cuotasExigibles,
        int cuotasPagadasATiempo,
        int cuotasPagadasConAtraso,
        int cuotasPendientesOVencidas,
        BigDecimal porcentajeRegularidad,
        String segmentoSugerido,
        String observacion
) {
}
