package pe.municipalidad.tributario.etl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/*
 * RF-040: un caso de la bandeja de homologación, sin datos personales (RNF-018). atributosComparados trae solo los
 * puntajes y distancias que calculó el ETL (nombre, documento, domicilio); los datos en claro están en el detalle.
 */
public record CasoHomologacionRespuesta(
        Long idCaso,
        Long idLote,
        LocalDate fechaCorte,
        TipoEntidad tipoEntidad,
        NivelHomologacion nivelAlcanzado,
        BigDecimal puntaje,
        EstadoCaso estado,
        LocalDateTime fechaCreacion,
        Maestro candidato,
        Maestro provisional,
        JsonNode atributosComparados) {

    public record Maestro(Long idMaestro, String seudonimo, EstadoMaestro estado) {

        static Maestro desde(EntidadMaestra entidad) {
            return entidad == null ? null
                    : new Maestro(entidad.getIdMaestro(), entidad.getSeudonimo(), entidad.getEstado());
        }
    }

    static CasoHomologacionRespuesta desde(CasoHomologacion caso, JsonNode atributos) {
        EntidadMaestra referencia = caso.getMaestroCandidato() != null
                ? caso.getMaestroCandidato() : caso.getMaestroProvisional();
        return new CasoHomologacionRespuesta(
                caso.getIdCaso(),
                caso.getLote().getIdLote(),
                caso.getLote().getFechaCorte(),
                referencia == null ? null : referencia.getTipoEntidad(),
                caso.getNivelAlcanzado(),
                caso.getPuntaje(),
                caso.getEstado(),
                caso.getFechaCreacion(),
                Maestro.desde(caso.getMaestroCandidato()),
                Maestro.desde(caso.getMaestroProvisional()),
                atributos);
    }
}
