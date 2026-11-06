package pe.municipalidad.tributario.arbitrio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;

/*
 * HU005: detalle de los arbitrios de un predio a la fecha de corte, mes a mes y con lo que corresponde a cada
 * servicio (barrido, recolección, parques y jardines, serenazgo), como los emite la ordenanza de San Miguel (V31).
 */
public record ArbitrioPredioRespuesta(
        PredioResumen predio,
        LocalDate fechaCorte,
        Resumen resumen,
        List<CuotaArbitrio> cuotas) {

    public record PredioResumen(
            String codigoPredio,
            String ubicacion,
            String usoPredio,
            String zona,
            List<Titular> titulares) {
    }

    public record Titular(
            String codigoContribuyente,
            String nombre,
            String condicionPropiedad,
            BigDecimal porcentajePropiedad) {
    }

    public record Resumen(
            BigDecimal totalEmitido,
            BigDecimal totalPagado,
            BigDecimal totalDescontado,
            BigDecimal totalSaldo,
            int cuotasPagadas,
            int cuotasPendientes,
            int cuotasVencidas,
            BigDecimal totalInteresMoratorio,
            List<TotalServicio> porServicio) {
    }

    public record TotalServicio(String servicio, String descripcion, BigDecimal emitido) {
    }

    /* Una cuota por periodo y titular: con copropiedad cada titular tiene sus propias cuotas. */
    public record CuotaArbitrio(
            Long idObligacion,
            String codigoContribuyente,
            int anio,
            int cuota,
            String periodo,
            LocalDate fechaVencimiento,
            BigDecimal emitido,
            BigDecimal pagado,
            BigDecimal descontado,
            BigDecimal saldo,
            EstadoObligacion estado,
            long diasVencida,
            BigDecimal interesMoratorio,
            List<EstadoCuentaFilaRespuesta.ServicioRespuesta> servicios) {
    }
}
