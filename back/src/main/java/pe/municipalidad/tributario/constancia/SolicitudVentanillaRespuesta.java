package pe.municipalidad.tributario.constancia;

import java.time.LocalDateTime;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * Solicitud de Constancia vista en ventanilla (HU042 por pasos). La deuda solo viene en la verificación, porque se
 * consulta en SAMNET en ese momento; la orden aparece al generarla y el número al emitir la constancia.
 */
public record SolicitudVentanillaRespuesta(
        Long idSolicitud,
        CanalSolicitud canal,
        EstadoConstancia estado,
        LocalDateTime fechaSolicitud,
        String documentoContribuyente,
        String tipoDocumento,
        String codigoContribuyente,
        String nombreContribuyente,
        String motivoRechazo,
        SolicitudConstanciaRespuesta.FirmaRespuesta firmaContribuyente,
        OrdenPagoConstanciaRespuesta ordenPago,
        DeudaConstanciaRespuesta deuda,
        String numeroConstancia) {

    public static SolicitudVentanillaRespuesta desde(SolicitudConstancia solicitud, Contribuyente contribuyente,
            TransaccionPago ordenPago, DeudaConstanciaRespuesta deuda, String numeroConstancia) {
        return new SolicitudVentanillaRespuesta(
                solicitud.getIdSolicitud(),
                solicitud.getCanal(),
                solicitud.getEstado(),
                solicitud.getFechaSolicitud(),
                solicitud.getDocumentoContribuyente(),
                contribuyente.getTipoDocumento().name(),
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                solicitud.getMotivoRechazo(),
                SolicitudConstanciaRespuesta.desde(solicitud).firmaContribuyente(),
                ordenPago == null ? null : OrdenPagoConstanciaRespuesta.desde(ordenPago, null, null),
                deuda,
                numeroConstancia);
    }
}
