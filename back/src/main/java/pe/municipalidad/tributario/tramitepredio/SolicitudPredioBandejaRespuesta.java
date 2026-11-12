package pe.municipalidad.tributario.tramitepredio;

import java.time.LocalDateTime;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/* HU043: fila de la bandeja de Ventanilla. El detalle con adjuntos y deuda se pide por solicitud. */
public record SolicitudPredioBandejaRespuesta(
        Long idSolicitud,
        TipoTramitePredio tipoTramite,
        TipoPredio tipoPredio,
        EstadoSolicitudPredio estado,
        LocalDateTime fechaEnvio,
        String documentoContribuyente,
        String codigoPredio,
        int cantidadAdjuntos,
        String revisor,
        LocalDateTime fechaRevision) {

    static SolicitudPredioBandejaRespuesta desde(SolicitudPredio solicitud) {
        return new SolicitudPredioBandejaRespuesta(solicitud.getIdSolicitud(), solicitud.getTipoTramite(),
                solicitud.getTipoPredio(), solicitud.getEstado(), solicitud.getFechaEnvio(),
                solicitud.getDocumentoContribuyente(), solicitud.getCodigoPredio(), solicitud.getAdjuntos().size(),
                solicitud.getRevisor() == null ? null : solicitud.getRevisor().getNombreUsuario(),
                solicitud.getFechaRevision());
    }
}
