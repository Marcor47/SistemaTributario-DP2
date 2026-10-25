package pe.municipalidad.tributario.constancia;

/* Documento de la solicitud que el contribuyente firma con el firmador del Estado (HU015). */
public record DocumentoFirmaRespuesta(
        Long idSolicitud,
        String nombreArchivo,
        String tipoContenido,
        String contenidoBase64,
        String hashSha256,
        boolean documentoDePrueba) {
}
