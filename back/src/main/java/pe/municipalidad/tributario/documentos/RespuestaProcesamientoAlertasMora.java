package pe.municipalidad.tributario.documentos;

import java.time.LocalDate;
import java.util.List;

/*
 * HU012 (RF-013): Resumen del procesamiento de alertas tempranas de morosidad.
 * Detalla cuotas evaluadas, cuotas vencidas detectadas, correos enviados dentro de las 24 h,
 * contribuyentes omitidos por no tener correo registrado (sin recurrir a SMS/WhatsApp/IVR)
 * y el listado de avisos generados.
 */
public record RespuestaProcesamientoAlertasMora(
        Long idLote,
        LocalDate fechaCorte,
        int cuotasEvaluadas,
        int cuotasVencidasDetectadas,
        int alertasEnviadas,
        int sinCorreoRegistrado,
        int alertasFallidas,
        List<AvisoCorreoRespuesta> avisos) {
}

