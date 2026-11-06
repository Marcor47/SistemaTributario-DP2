package pe.municipalidad.tributario.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/*
 * HU037 / RF-050: reporte de Principales Contribuyentes (PRICOS) a la fecha de corte de la última carga publicada
 * del ETL. Las columnas son las de RF-050, sin usuario ni clave de pagos en línea (pendientes por RNF-018).
 */
public record ReportePricosRespuesta(
        Long idLote,
        LocalDate fechaCorte,
        CriterioPricos criterio,
        int limite,
        int totalContribuyentes,
        List<FilaPricos> filas) {

    public record FilaPricos(
            int posicion,
            String codigo,
            String razonSocial,
            String regimen,
            Documento documento,
            DomicilioFiscal domicilioFiscal,
            BigDecimal baseImponible,
            Tributos emisionAnioCorte,
            Tributos deudaAniosAnteriores,
            DeudaAnioCorte deudaAnioCorte,
            String condicion,
            String tipoPersona,
            String correo,
            String telefonos) {
    }

    public record Documento(String tipo, String numero) {
    }

    public record DomicilioFiscal(
            String direccion,
            String via,
            String numero,
            String departamento,
            String interior,
            String manzana,
            String lote,
            String distrito) {
    }

    /* Predial y arbitrios de un mismo concepto; los arbitrios se manejan como un solo tributo. */
    public record Tributos(BigDecimal predial, BigDecimal arbitrios) {
    }

    /* Deuda del año de la fecha de corte; el porcentaje de descuento es el aplicado a los arbitrios. */
    public record DeudaAnioCorte(BigDecimal predial, BigDecimal arbitrios, BigDecimal porcentajeDescuentoArbitrios) {
    }
}
