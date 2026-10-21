package pe.municipalidad.tributario.cobranza;

import java.time.LocalDate;
import java.util.List;

/* Respuesta de GET /api/obligaciones/{id}/etapa-cobranza. */
public record EtapaCobranzaRespuesta(
        ObligacionResumen obligacion,
        LocalDate fechaConsulta,
        long diasVencida,
        EtapaRespuesta etapaActual,
        long diasEnEtapaActual,
        List<EtapaRespuesta> historial) {
}
