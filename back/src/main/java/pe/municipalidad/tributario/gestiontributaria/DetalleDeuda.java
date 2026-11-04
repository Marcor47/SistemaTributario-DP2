package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Detalle de una obligación vencida de la cartera del contribuyente. Se usa en el reporte de morosos para
 * desglosar la deuda por año, tributo y cuota sin y con beneficio.
 */
public record DetalleDeuda(
        Integer anio,
        String codigoPredio,
        String zona,
        Tributo tributo,
        Integer periodo,
        BigDecimal montoSinBeneficio,
        BigDecimal montoConBeneficio,
        LocalDate fechaVencimiento,
        Integer diasVencido,
        EtapaCobranza etapaCobranza,
        BigDecimal montoEmitido,
        BigDecimal montoPagado,
        BigDecimal montoDescontado) {
}
