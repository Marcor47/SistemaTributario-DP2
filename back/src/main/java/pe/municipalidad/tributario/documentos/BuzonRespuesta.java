package pe.municipalidad.tributario.documentos;

import java.util.List;

/* HU019: buzón del contribuyente, del documento más reciente al más antiguo. */
public record BuzonRespuesta(
        int total,
        int sinLeer,
        List<DocumentoBuzonRespuesta> documentos) {

    public static BuzonRespuesta de(List<DocumentoBuzon> documentos) {
        List<DocumentoBuzonRespuesta> items = documentos.stream().map(DocumentoBuzonRespuesta::desde).toList();
        int sinLeer = (int) items.stream().filter(documento -> !documento.leido()).count();
        return new BuzonRespuesta(items.size(), sinLeer, items);
    }
}
