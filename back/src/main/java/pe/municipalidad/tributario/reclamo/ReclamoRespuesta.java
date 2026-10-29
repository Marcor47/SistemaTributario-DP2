package pe.municipalidad.tributario.reclamo;

import java.time.LocalDateTime;
import java.util.List;

public record ReclamoRespuesta(
        Long idReclamo,
        String numeroReclamo,
        TipoReclamante tipoReclamante,
        TipoReclamo tipoReclamo,
        String referencia,
        LocalDateTime fechaHoraIncidente,
        String comentario,
        EstadoReclamo estado,
        LocalDateTime fechaRegistro,
        String respuesta,
        LocalDateTime fechaRespuesta,
        List<DerivacionRespuesta> derivaciones) {

    public record DerivacionRespuesta(String areaDestino, String responsable, String derivadoPor,
            LocalDateTime fechaDerivacion, String observacion) {
    }

    public static ReclamoRespuesta desde(Reclamo reclamo) {
        List<DerivacionRespuesta> derivaciones = reclamo.getDerivaciones().stream()
                .map(derivacion -> new DerivacionRespuesta(
                        derivacion.getAreaDestino().getNombre(),
                        derivacion.getResponsable().getNombreCompleto(),
                        derivacion.getDerivadoPor().getNombreCompleto(),
                        derivacion.getFechaDerivacion(),
                        derivacion.getObservacion()))
                .toList();
        return new ReclamoRespuesta(reclamo.getIdReclamo(), reclamo.getNumeroReclamo(),
                reclamo.getTipoReclamante(), reclamo.getTipoReclamo(), reclamo.getReferencia(),
                reclamo.getFechaHoraIncidente(), reclamo.getComentario(), reclamo.getEstado(),
                reclamo.getFechaRegistro(), reclamo.getRespuesta(), reclamo.getFechaRespuesta(), derivaciones);
    }
}
