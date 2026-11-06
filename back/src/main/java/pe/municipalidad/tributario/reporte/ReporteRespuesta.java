package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.contribuyente.DomicilioFiscal;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;
import pe.municipalidad.tributario.gestiontributaria.DetalleDeuda;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU006, HU018 y HU038: reporte del catálogo generado con los datos de SAMNET a la fecha de corte. filas es FilaMoroso, FilaPrico
 * o FilaBuenPagador según tipoReporte. filtrosAplicados.anio es el año usado.
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

    /* HU006 y HU018 (RF-024): contribuyente moroso con razón social, régimen, documento, domicilio desglosado y deuda sin beneficio. */
    public record FilaMoroso(
            String codigo,
            String nombre,
            String razonSocial,
            String regimen,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String condicion,
            TipoPersona tipoPersona,
            String correo,
            String telefonos,
            String domicilioFiscal,
            DomicilioFiscal domicilioFiscalDesglosado,
            List<String> zonas,
            BigDecimal deudaVencida,
            BigDecimal deudaSinBeneficio,
            int cuotasVencidas,
            LocalDate vencimientoMasAntiguo,
            long diasVencido,
            EtapaCobranza etapaCobranza,
            String nombreEtapa,
            List<MontoPorTributo> deudaPorTributo,
            List<DetalleDeuda> detalleDeuda) {

        public FilaMoroso(
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
            this(codigo, nombre, nombre, null, tipoDocumento, numeroDocumento, null, null, null, null, domicilioFiscal,
                    null, zonas, deudaVencida, deudaVencida, cuotasVencidas, vencimientoMasAntiguo,
                    diasVencido, etapaCobranza, nombreEtapa, deudaPorTributo, List.of());
        }
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

    /* HU006 y HU038 (RF-051): contribuyente puntual con razón social, régimen, documento, domicilio y montos por tributo. */
    public record FilaBuenPagador(
            String codigo,
            String nombre,
            String razonSocial,
            String regimen,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String domicilioFiscal,
            DomicilioFiscal domicilioFiscalDesglosado,
            List<String> zonas,
            BigDecimal montoEmitidoPredial,
            BigDecimal montoEmitidoArbitrios,
            BigDecimal montoPagadoPredial,
            BigDecimal montoPagadoArbitrios,
            BigDecimal totalEmitido,
            BigDecimal totalPagado,
            int cuotasPagadasATiempo,
            BigDecimal pagadoAnio) {

        public FilaBuenPagador(
                String codigo,
                String nombre,
                TipoDocumento tipoDocumento,
                String numeroDocumento,
                String domicilioFiscal,
                List<String> zonas,
                int cuotasPagadasATiempo,
                BigDecimal pagadoAnio) {
            this(codigo, nombre, nombre, "GENERAL", tipoDocumento, numeroDocumento, domicilioFiscal, null, zonas,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, pagadoAnio, pagadoAnio, pagadoAnio,
                    cuotasPagadasATiempo, pagadoAnio);
        }
    }

    public record MontoPorTributo(Tributo tributo, BigDecimal monto) {
    }
}
