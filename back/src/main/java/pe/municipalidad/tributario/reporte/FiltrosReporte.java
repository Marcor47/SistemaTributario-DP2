package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.util.Locale;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Filtros de un reporte del catálogo; todos son opcionales. anio por defecto es el año de la fecha de corte.
 * En HU018 (RF-024) se soporta un rango de años y el filtro de beneficio para morosos.
 * regimen, tipoDocumento y etapa solo se aplican al reporte de morosos.
 * montoMinimo se compara con el monto principal del reporte: deuda vencida, monto emitido o monto pagado.
 */
public record FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo,
        String regimen, TipoDocumento tipoDocumento, EtapaCobranza etapa,
        Integer anioDesde, Integer anioHasta, FiltroBeneficio beneficio,
        CriterioPricos criterio, Integer limite) {

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo,
            String regimen, TipoDocumento tipoDocumento, EtapaCobranza etapa,
            Integer anioDesde, Integer anioHasta, FiltroBeneficio beneficio) {
        this(anio, zona, tributo, montoMinimo, periodo, regimen, tipoDocumento, etapa,
                anioDesde, anioHasta, beneficio, null, null);
    }

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo,
            String regimen, TipoDocumento tipoDocumento, EtapaCobranza etapa) {
        this(anio, zona, tributo, montoMinimo, periodo, regimen, tipoDocumento, etapa, null, null, null, null, null);
    }

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo, Integer periodo) {
        this(anio, zona, tributo, montoMinimo, periodo, null, null, null, null, null, null, null, null);
    }

    public FiltrosReporte(Integer anio, String zona, Tributo tributo, BigDecimal montoMinimo) {
        this(anio, zona, tributo, montoMinimo, null, null, null, null, null, null, null, null, null);
    }

    public FiltrosReporte {
        zona = zona == null || zona.isBlank() ? null : zona.strip().toUpperCase(Locale.ROOT);
        regimen = regimen == null || regimen.isBlank() ? null : regimen.strip().toUpperCase(Locale.ROOT);
        if (anio == null && periodo != null) {
            anio = periodo;
        }
        if (anioDesde == null && anioHasta == null) {
            Integer unico = periodo != null ? periodo : anio;
            if (unico != null) {
                anioDesde = unico;
                anioHasta = unico;
            }
        }
        if (anioDesde != null && anioHasta == null) {
            anioHasta = anioDesde;
        }
        if (anioHasta != null && anioDesde == null) {
            anioDesde = anioHasta;
        }
        if (beneficio == null) {
            beneficio = FiltroBeneficio.AMBOS;
        }
        if (criterio != null && limite == null) limite = 10;
    }

    FiltrosReporte conAnio(Integer anioAplicado) {
        return new FiltrosReporte(anioAplicado, zona, tributo, montoMinimo, periodo, regimen, tipoDocumento, etapa,
                anioDesde, anioHasta, beneficio, criterio, limite);
    }

    FiltrosReporte conRangoYBeneficio(Integer anioAplicado, Integer desde, Integer hasta, FiltroBeneficio filtro) {
        return new FiltrosReporte(anioAplicado, zona, tributo, montoMinimo, periodo, regimen, tipoDocumento, etapa,
                desde, hasta, filtro, criterio, limite);
    }
}
