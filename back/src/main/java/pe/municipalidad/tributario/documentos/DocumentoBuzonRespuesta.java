package pe.municipalidad.tributario.documentos;

import java.time.LocalDateTime;

/*
 * HU019 / RF-030: documento del buzón con la fecha de envío y la de primera lectura. Es null mientras el
 * contribuyente no lo abre.
 */
public record DocumentoBuzonRespuesta(
        Long idDocumento,
        TipoDocumentoBuzon tipo,
        String titulo,
        String archivo,
        LocalDateTime fechaEnvio,
        LocalDateTime fechaPrimeraLectura,
        boolean leido,
        boolean destacado,
        boolean archivado,
        LocalDateTime fechaArchivado,
        String contenido,
        String urlDescarga) {

    public static DocumentoBuzonRespuesta desde(DocumentoBuzon documento) {
        return new DocumentoBuzonRespuesta(
                documento.getIdDocumento(),
                documento.getTipo(),
                documento.getTitulo(),
                documento.getArchivo(),
                documento.getFechaEnvio(),
                documento.getFechaPrimeraLectura(),
                documento.isLeidoActualmente(),
                documento.isDestacado(),
                documento.isArchivado(),
                documento.getFechaArchivado(),
                contenido(documento),
                descargable(documento) ? "/buzon/" + documento.getIdDocumento() + "/descarga" : null);
    }

    /* La constancia y la resolución de un trámite de predio se descargan en PDF con la plantilla institucional. */
    private static boolean descargable(DocumentoBuzon documento) {
        return documento.getConstancia() != null || (documento.getSolicitudPredio() != null
                && documento.getSolicitudPredio().getEstado()
                        != pe.municipalidad.tributario.tramitepredio.EstadoSolicitudPredio.EN_REVISION);
    }

    private static String contenido(DocumentoBuzon documento) {
        if (documento.getContenido() != null) return documento.getContenido();
        if (documento.getReclamo() != null) return documento.getReclamo().getRespuesta();
        if (documento.getSolicitudPredio() != null) {
            var solicitud = documento.getSolicitudPredio();
            return "Solicitud #" + solicitud.getIdSolicitud() + " · " + solicitud.getTipoTramite()
                    + " · " + solicitud.getEstado()
                    + (solicitud.getCodigoPredio() == null ? "" : "\nPredio: " + solicitud.getCodigoPredio())
                    + (solicitud.getMotivoRechazo() == null ? "" : "\nMotivo: " + solicitud.getMotivoRechazo())
                    + (solicitud.getDeudaPredio() == null ? ""
                            : "\nDeuda existente del predio: S/ " + solicitud.getDeudaPredio().toPlainString());
        }
        return null;
    }
}
