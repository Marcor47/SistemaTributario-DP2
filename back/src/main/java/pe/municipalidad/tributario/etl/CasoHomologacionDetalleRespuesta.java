package pe.municipalidad.tributario.etl;

import java.util.List;

/*
 * RF-040: lo que el Responsable de datos compara para decidir. Trae los registros de SAMNET en claro de cada lado
 * (de la capa plata, último lote publicado); por eso cada consulta queda en la auditoría como una reidentificación.
 */
public record CasoHomologacionDetalleRespuesta(
        CasoHomologacionRespuesta caso,
        Lado candidato,
        Lado provisional) {

    public record Lado(Long idMaestro, String seudonimo, EstadoMaestro estado, List<RegistroOrigen> registros) {
    }

    /* Para un predio: documento es el código catastral y nombre la ubicación. */
    public record RegistroOrigen(
            String codigo,
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String domicilio,
            String tipoPersona,
            String estado,
            String nivelHomologacion) {
    }
}
