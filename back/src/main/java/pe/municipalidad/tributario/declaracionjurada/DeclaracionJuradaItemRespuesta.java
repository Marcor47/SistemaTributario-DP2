package pe.municipalidad.tributario.declaracionjurada;

import java.math.BigDecimal;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;

public record DeclaracionJuradaItemRespuesta(
        String idDeclaracion,
        int anio,
        BigDecimal autoavaluo,
        String motivo,
        String submotivo,
        String tipoDeclarante,
        String estado,
        boolean esVigente) {

    public static DeclaracionJuradaItemRespuesta desde(DeclaracionJurada declaracion, boolean esVigente) {
        return new DeclaracionJuradaItemRespuesta(
                declaracion.idDeclaracion(),
                declaracion.anio(),
                declaracion.autoavaluo(),
                declaracion.motivo(),
                declaracion.submotivo(),
                declaracion.tipoDeclarante(),
                declaracion.estado(),
                esVigente);
    }
}
