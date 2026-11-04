package pe.municipalidad.tributario.ia;

import java.util.List;

public record AprobacionSegmentosRespuesta(List<SegmentoRespuesta> publicados, List<Long> idsRetirados) {
}
