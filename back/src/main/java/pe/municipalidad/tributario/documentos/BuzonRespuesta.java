package pe.municipalidad.tributario.documentos;

import java.util.List;
import org.springframework.data.domain.Page;

/* HU019: buzón del contribuyente, del documento más reciente al más antiguo. */
public record BuzonRespuesta(
        int total,
        int sinLeer,
        List<DocumentoBuzonRespuesta> documentos,
        int pagina,
        int tamanio,
        int totalPaginas) {

    public static BuzonRespuesta de(List<DocumentoBuzon> documentos) {
        List<DocumentoBuzonRespuesta> items = documentos.stream().map(DocumentoBuzonRespuesta::desde).toList();
        int sinLeer = (int) items.stream().filter(documento -> !documento.leido()).count();
        return new BuzonRespuesta(items.size(), sinLeer, items, 0, Math.max(1, items.size()), items.isEmpty() ? 0 : 1);
    }

    public static BuzonRespuesta de(Page<DocumentoBuzon> pagina, long sinLeer) {
        return new BuzonRespuesta(Math.toIntExact(pagina.getTotalElements()), Math.toIntExact(sinLeer),
                pagina.getContent().stream().map(DocumentoBuzonRespuesta::desde).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalPages());
    }
}
