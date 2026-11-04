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
        boolean leido) {

    public static DocumentoBuzonRespuesta desde(DocumentoBuzon documento) {
        return new DocumentoBuzonRespuesta(
                documento.getIdDocumento(),
                documento.getTipo(),
                documento.getTitulo(),
                documento.getArchivo(),
                documento.getFechaEnvio(),
                documento.getFechaPrimeraLectura(),
                documento.getFechaPrimeraLectura() != null);
    }
}
