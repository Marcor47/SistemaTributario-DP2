package pe.municipalidad.tributario.cobranza;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.Tributo;

public record ObligacionResumen(
        Long id,
        String contribuyenteCodigo,
        String contribuyenteNombre,
        Tributo tributo,
        String tributoDescripcion,
        String periodo,
        String codigoPredio,
        LocalDate fechaVencimiento,
        BigDecimal montoEmitido,
        BigDecimal montoPagado,
        BigDecimal saldo) {

    public static ObligacionResumen desde(Obligacion obligacion, Contribuyente contribuyente) {
        return new ObligacionResumen(
                obligacion.getIdObligacion(),
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                obligacion.getTributo(),
                obligacion.getTributo().getDescripcion(),
                obligacion.etiquetaPeriodo(),
                obligacion.getCodigoPredio(),
                obligacion.getFechaVencimiento(),
                obligacion.getMontoEmitido(),
                obligacion.getMontoPagado(),
                obligacion.saldo());
    }
}
