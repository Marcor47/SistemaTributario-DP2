package pe.municipalidad.tributario.constancia;

import java.time.LocalDateTime;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * Solicitud de constancia vista por el operador (HU020). La deuda solo viene en el detalle y en la verificación,
 * porque se consulta en SAMNET en ese momento; en la bandeja es nula.
 */
public record SolicitudGestionRespuesta(
        Long idSolicitud,
        CanalSolicitud canal,
        EstadoConstancia estado,
        LocalDateTime fechaSolicitud,
        String documentoContribuyente,
        String nombreContribuyente,
        String codigoContribuyente,
        String tipoDocumentoContribuyente,
        String operador,
        LocalDateTime fechaRevision,
        String motivoRechazo,
        SolicitudConstanciaRespuesta.FirmaRespuesta firmaContribuyente,
        OrdenPagoConstanciaRespuesta ordenPago,
        String numeroConstancia,
        DeudaConstanciaRespuesta deuda) {

    static SolicitudGestionRespuesta desde(SolicitudConstancia solicitud, TransaccionPago ordenPago,
            String numeroConstancia, DeudaConstanciaRespuesta deuda, Contribuyente contribuyente) {
        return new SolicitudGestionRespuesta(
                solicitud.getIdSolicitud(),
                solicitud.getCanal(),
                solicitud.getEstado(),
                solicitud.getFechaSolicitud(),
                solicitud.getDocumentoContribuyente(),
                contribuyente == null ? null : contribuyente.getNombreRazonSocial(),
                contribuyente == null ? null : contribuyente.getCodigoContribuyente(),
                contribuyente == null ? null : contribuyente.getTipoDocumento().name(),
                solicitud.getOperador() == null ? null : solicitud.getOperador().getNombreUsuario(),
                solicitud.getFechaRevision(),
                solicitud.getMotivoRechazo(),
                SolicitudConstanciaRespuesta.desde(solicitud).firmaContribuyente(),
                ordenPago == null ? null : OrdenPagoConstanciaRespuesta.desde(ordenPago, null, null),
                numeroConstancia,
                deuda);
    }
}
