package pe.municipalidad.tributario.predios;

import java.util.List;

/* Una página de resultados: un contribuyente puede tener decenas de predios y la lista debe poder recorrerse. */
public record PaginaPrediosRespuesta(List<PredioRespuesta> contenido, int pagina, int tamano, int totalElementos,
        int totalPaginas) {
}
