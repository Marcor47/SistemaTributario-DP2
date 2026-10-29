package pe.municipalidad.tributario.pagosbeneficios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.gestiontributaria.CanalPago;
import pe.municipalidad.tributario.gestiontributaria.TipoBeneficio;

/*
 * HU048 / RF-062: pagos de un contribuyente con el beneficio aplicado y lo que correspondía según el estado de
 * cuenta de SAMNET, a la fecha de corte. El estado de cuenta se incluye siempre, incluso sin pagos.
 */
public record PagosBeneficiosRespuesta(
        ContribuyenteResumen contribuyente,
        LocalDate fechaCorte,
        Resumen resumen,
        EstadoCuentaResumen estadoCuenta,
        List<PagoDetalle> pagos,
        List<BeneficioVigente> beneficiosVigentes) {

    public record ContribuyenteResumen(
            String codigo,
            String tipoDocumento,
            String numeroDocumento,
            String nombre) {
    }

    public record Resumen(
            int cantidadPagos,
            int pagosConBeneficio,
            BigDecimal totalPagado,
            BigDecimal totalCorrespondiente,
            BigDecimal totalDiferencia) {
    }

    public record EstadoCuentaResumen(
            BigDecimal totalEmitido,
            BigDecimal totalPagado,
            BigDecimal totalDeuda,
            int obligacionesVencidas) {
    }

    /*
     * diferencia = montoPagado - montoCorrespondiente: negativa si se pagó menos de lo que correspondía.
     * beneficioAplicado es el identificador del beneficio (null si el pago no tuvo beneficio); su detalle,
     * si sigue vigente, está en beneficiosVigentes.
     */
    public record PagoDetalle(
            String idPago,
            LocalDate fechaPago,
            CanalPago canal,
            BigDecimal montoPagado,
            BigDecimal montoCorrespondiente,
            BigDecimal diferencia,
            String beneficioAplicado) {
    }

    public record BeneficioVigente(
            String idBeneficio,
            TipoBeneficio tipo,
            BigDecimal porcentajeDescuento,
            LocalDate vigenciaDesde,
            LocalDate vigenciaHasta) {
    }
}
