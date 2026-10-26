package pe.municipalidad.tributario.tramitepredio;

import java.time.LocalDateTime;
import java.util.List;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

public record SolicitudPredioRespuesta(Long idSolicitud, TipoTramitePredio tipoTramite, TipoPredio tipoPredio,
        String codigoPredio, EstadoSolicitudPredio estado, LocalDateTime fechaEnvio,
        List<AdjuntoRespuesta> adjuntos) {
    public static SolicitudPredioRespuesta desde(SolicitudPredio solicitud) {
        return new SolicitudPredioRespuesta(solicitud.getIdSolicitud(), solicitud.getTipoTramite(),
                solicitud.getTipoPredio(), solicitud.getCodigoPredio(), solicitud.getEstado(),
                solicitud.getFechaEnvio(), solicitud.getAdjuntos().stream()
                        .map(a -> new AdjuntoRespuesta(a.getIdAdjunto(), a.getRequisito().getIdRequisito(),
                                a.getNombreOriginal(), a.getTipoContenido(), a.getTamanoBytes()))
                        .toList());
    }

    public record AdjuntoRespuesta(Long id, Long idRequisito, String nombre, TipoContenido tipo, long tamano) {
    }
}
