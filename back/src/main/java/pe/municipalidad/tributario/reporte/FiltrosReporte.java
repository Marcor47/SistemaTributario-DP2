package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.util.Locale;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Filtros de un reporte del catálogo; todos son opcionales. anio no aplica a MOROSOS (su deuda es de todos los
 * años) y, en los demás, por defecto es el año de la fecha de corte. montoMinimo se compara con el monto principal
 * del reporte: deuda vencida, monto emitido o monto pagado.
 */
public record FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo) {

    public FiltrosReporte {
        zona = zona == null || zona.isBlank() ? null : zona.strip().toUpperCase(Locale.ROOT);
    }

    FiltrosReporte conAnio(Integer anioAplicado) {
        return new FiltrosReporte(anioAplicado, zona, tributo, montoMinimo);
    }
}
