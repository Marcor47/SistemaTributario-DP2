package pe.municipalidad.tributario.declaracionjurada;

import java.time.LocalDate;
import java.util.List;

public record DeclaracionJuradaRespuesta(
        PredioIdentificacionRespuesta predio,
        DeclaracionJuradaItemRespuesta declaracionVigente,
        List<DeclaracionJuradaItemRespuesta> historial,
        LocalDate fechaConsulta,
        List<AdvertenciaRespuesta> advertencias) {
}
