package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;

/* Declaración Jurada del predio; su autoavalúo es la fuente de la base imponible (RF-003). */
public record DeclaracionJurada(
        String idDeclaracion,
        String codigoPredio,
        int anio,
        BigDecimal autoavaluo,
        String motivo,
        String submotivo,
        String tipoDeclarante,
        String estado,
        boolean esVigente) {
}
