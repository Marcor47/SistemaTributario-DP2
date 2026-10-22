package pe.municipalidad.tributario.estadocuenta;

import java.time.LocalDate;
import java.util.List;

/**
 * HU004 - respuesta completa del estado de cuenta.
 * Estructura pensada para que el front solo cambie el body de obtenerEstadoCuenta()
 * en cuentaService.js por un fetch a este endpoint cuando el ETL este disponible.
 */
public record EstadoCuentaRespuesta(
        ContribuyenteCuentaRespuesta contribuyente,
        LocalDate fechaCorte,
        boolean desactualizado,
        List<EstadoCuentaFilaRespuesta> filas) {
}
