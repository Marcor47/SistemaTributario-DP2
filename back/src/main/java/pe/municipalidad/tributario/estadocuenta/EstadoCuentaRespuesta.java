package pe.municipalidad.tributario.estadocuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU004 / HU014 - estado de cuenta completo. resumenAnual separa, por año, cuánto es Impuesto Predial y cuánto
 * arbitrios; predios son los del contribuyente, para agrupar sus arbitrios. timMensual es la tasa con que se
 * calculó el interés moratorio de las cuotas vencidas.
 */
public record EstadoCuentaRespuesta(
        ContribuyenteCuentaRespuesta contribuyente,
        LocalDate fechaCorte,
        boolean desactualizado,
        BigDecimal timMensual,
        List<ResumenAnual> resumenAnual,
        List<PredioCuenta> predios,
        List<EstadoCuentaFilaRespuesta> filas) {

    public boolean esUltimaCopia() {
        return desactualizado;
    }

    public record ResumenAnual(int anio, Totales impuestoPredial, Totales arbitrios, Totales total) {
    }

    /* deudaVencida es la parte de la deuda ya exigible; el interés moratorio corre solo sobre ella. */
    public record Totales(BigDecimal emitido, BigDecimal pagado, BigDecimal deuda, BigDecimal deudaVencida,
            BigDecimal interesMoratorio, BigDecimal totalAPagar) {
    }

    public record PredioCuenta(String codigoPredio, String ubicacion, String usoPredio, String zona,
            BigDecimal porcentajePropiedad) {
    }
}
