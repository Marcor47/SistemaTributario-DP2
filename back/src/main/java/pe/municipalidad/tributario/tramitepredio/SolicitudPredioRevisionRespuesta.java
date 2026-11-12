package pe.municipalidad.tributario.tramitepredio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/*
 * HU043 / RF-056: solicitud de predio vista por Ventanilla, con el tipo de trámite y de predio, los documentos
 * adjuntos y la deuda existente del predio. Mientras está EN_REVISION la deuda se consulta en SAMNET en ese momento;
 * ya decidida, es la cifra guardada al decidir y obligacionesPendientes va vacía. contribuyente es nulo si SAMNET no
 * lo registra (un REGISTRO no puede aceptarse así); predio es nulo si la solicitud aún no tiene predio en SAMNET.
 */
public record SolicitudPredioRevisionRespuesta(
        Long idSolicitud,
        TipoTramitePredio tipoTramite,
        TipoPredio tipoPredio,
        EstadoSolicitudPredio estado,
        LocalDateTime fechaEnvio,
        boolean firmaElectronica,
        String documentoContribuyente,
        ContribuyenteResumen contribuyente,
        String codigoPredio,
        PredioResumen predio,
        List<AdjuntoRevision> adjuntos,
        BigDecimal deudaPredio,
        List<EstadoCuentaFilaRespuesta> obligacionesPendientes,
        String revisor,
        LocalDateTime fechaRevision,
        String motivoRechazo) {

    public record ContribuyenteResumen(String codigoContribuyente, String nombre, String tipoDocumento,
            String numeroDocumento, String estado) {
    }

    public record PredioResumen(String codigoPredio, String ubicacion, String zona, String usoPredio,
            List<Titular> titulares) {
    }

    public record Titular(String codigoContribuyente, String condicionPropiedad, BigDecimal porcentajePropiedad) {
    }

    public record AdjuntoRevision(Long id, Long idRequisito, String requisito, boolean obligatorio, String nombre,
            TipoContenido tipo, long tamano, String urlDescarga) {

        static AdjuntoRevision desde(SolicitudPredio solicitud, DocumentoAdjunto adjunto) {
            return new AdjuntoRevision(adjunto.getIdAdjunto(), adjunto.getRequisito().getIdRequisito(),
                    adjunto.getRequisito().getNombreDocumento(), adjunto.getRequisito().isObligatorio(),
                    adjunto.getNombreOriginal(), adjunto.getTipoContenido(), adjunto.getTamanoBytes(),
                    "/gestion/solicitudes-predio/" + solicitud.getIdSolicitud() + "/adjuntos/"
                            + adjunto.getIdAdjunto());
        }
    }
}
