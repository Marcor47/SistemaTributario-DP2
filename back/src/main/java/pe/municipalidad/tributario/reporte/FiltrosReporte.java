package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.util.Locale;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Filtros de un reporte del catálogo; todos son opcionales. anio por defecto es el año de la fecha de corte.
 * En HU018 (RF-024) se soporta periodo/año para filtrar morosos por zona y periodo.
 * regimen, tipoDocumento y etapa solo se aplican al reporte de morosos.
 * montoMinimo se compara con el monto principal del reporte: deuda vencida, monto emitido o monto pagado.
 */
public record FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo,
        String regimen, TipoDocumento tipoDocumento, EtapaCobranza etapa) {

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo) {
        this(anio, zona, tributo, montoMinimo, periodo, null, null, null);
    }

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo) {
        this(anio, zona, tributo, montoMinimo, null, null, null, null);
    }

    public FiltrosReporte {
        zona = zona == null || zona.isBlank() ? null : zona.strip().toUpperCase(Locale.ROOT);
        regimen = regimen == null || regimen.isBlank() ? null : regimen.strip().toUpperCase(Locale.ROOT);
        if (anio == null && periodo != null) {
            anio = periodo;
        }
    }

    FiltrosReporte conAnio(Integer anioAplicado) {
        return new FiltrosReporte(anioAplicado, zona, tributo, montoMinimo, periodo, regimen, tipoDocumento, etapa);
    }
}
