package pe.municipalidad.tributario.ia;

import java.time.LocalDateTime;

/* variablesCaracteristicas es el JSON que entrega el modelo, sin reinterpretar. */
public record SegmentoRespuesta(
        Long idSegmento,
        String nombre,
        String descripcion,
        int tamano,
        String variablesCaracteristicas,
        EstadoSegmento estado,
        Long idModelo,
        String modelo,
        String versionModelo,
        String aprobador,
        LocalDateTime fechaAprobacion) {

    static SegmentoRespuesta desde(Segmento segmento) {
        return new SegmentoRespuesta(
                segmento.getIdSegmento(),
                segmento.getNombre(),
                segmento.getDescripcion(),
                segmento.getTamano(),
                segmento.getVariablesCaracteristicas(),
                segmento.getEstado(),
                segmento.getModelo().getIdModelo(),
                segmento.getModelo().getNombre(),
                segmento.getModelo().getVersion(),
                segmento.getAprobador() == null ? null : segmento.getAprobador().getNombreUsuario(),
                segmento.getFechaAprobacion());
    }
}
