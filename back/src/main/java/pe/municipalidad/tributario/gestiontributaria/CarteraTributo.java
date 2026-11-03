package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.DomicilioFiscal;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Situación de un contribuyente en un tributo para los reportes del catálogo (RF-024, RF-050, RF-051), según
 * ps_reportecartera. Los montos "del año" son del año pedido; la deuda vencida es de todos los años a la fecha de
 * corte. etapaCobranza es la más avanzada de sus cuotas vencidas impagas (null si no registra ninguna).
 * En HU018 (RF-024 adaptado) se incluye régimen, domicilio desglosado, deuda sin beneficio y montos del periodo.
 */
public record CarteraTributo(
        String codigoContribuyente,
        String nombre,
        TipoPersona tipoPersona,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String condicion,
        String correo,
        String telefonos,
        String domicilioFiscal,
        List<String> zonas,
        Tributo tributo,
        BigDecimal emitidoAnio,
        BigDecimal pagadoAnio,
        int cuotasExigiblesAnio,
        int cuotasATiempoAnio,
        BigDecimal deudaVencida,
        int cuotasVencidas,
        LocalDate vencimientoMasAntiguo,
        EtapaCobranza etapaCobranza,
        String regimen,
        DomicilioFiscal domicilioDesglosado,
        BigDecimal deudaSinBeneficio,
        BigDecimal deudaVencidaAnio,
        BigDecimal deudaSinBeneficioAnio,
        int cuotasVencidasAnio,
        String detalleDeuda) {

    public CarteraTributo(
            String codigoContribuyente,
            String nombre,
            TipoPersona tipoPersona,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String domicilioFiscal,
            List<String> zonas,
            Tributo tributo,
            BigDecimal emitidoAnio,
            BigDecimal pagadoAnio,
            int cuotasExigiblesAnio,
            int cuotasATiempoAnio,
            BigDecimal deudaVencida,
            int cuotasVencidas,
            LocalDate vencimientoMasAntiguo,
            EtapaCobranza etapaCobranza) {
        this(codigoContribuyente, nombre, tipoPersona, tipoDocumento, numeroDocumento, null, null, null, domicilioFiscal,
                zonas, tributo, emitidoAnio, pagadoAnio, cuotasExigiblesAnio, cuotasATiempoAnio,
                deudaVencida, cuotasVencidas, vencimientoMasAntiguo, etapaCobranza,
                null, null, deudaVencida, deudaVencida, deudaVencida, cuotasVencidas, null);
    }
}
