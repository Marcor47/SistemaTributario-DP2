package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU006: reporte del catálogo generado con los datos de SAMNET a la fecha de corte. filas es FilaMoroso, FilaPrico
 * o FilaBuenPagador según tipoReporte. filtrosAplicados.anio es el año usado (null en MOROSOS).
 */
public record ReporteRespuesta<T>(
        TipoReporte tipoReporte,
        String titulo,
        LocalDate fechaCorte,
        FiltrosReporte filtrosAplicados,
        Resumen resumen,
        List<T> filas) {

    /* montoTotal suma la columna conceptoMonto de las filas; umbralPrico solo viene en PRICOS. */
    public record Resumen(
            int cantidad,
            String conceptoMonto,
            BigDecimal montoTotal,
            BigDecimal umbralPrico) {
    }

    /* diasVencido se cuenta desde la cuota impaga más antigua. */
    public record FilaMoroso(
            String codigo,
            String nombre,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String domicilioFiscal,
            List<String> zonas,
            BigDecimal deudaVencida,
            int cuotasVencidas,
            LocalDate vencimientoMasAntiguo,
            long diasVencido,
            EtapaCobranza etapaCobranza,
            String nombreEtapa,
            List<MontoPorTributo> deudaPorTributo) {
    }

    /* porcentajePagado = pagadoAnio / emitidoAnio x 100. */
    public record FilaPrico(
            String codigo,
            String nombre,
            TipoPersona tipoPersona,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String domicilioFiscal,
            List<String> zonas,
            BigDecimal emitidoAnio,
            BigDecimal pagadoAnio,
            BigDecimal porcentajePagado,
            BigDecimal deudaVencida) {
    }

    public record FilaBuenPagador(
            String codigo,
            String nombre,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String domicilioFiscal,
            List<String> zonas,
            int cuotasPagadasATiempo,
            BigDecimal pagadoAnio) {
    }

    public record MontoPorTributo(Tributo tributo, BigDecimal monto) {
    }
}
