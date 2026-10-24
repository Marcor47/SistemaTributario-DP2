package pe.municipalidad.tributario.regularidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;

/**
 * HU010 - Logica de negocio para calcular la regularidad de pago del contribuyente.
 *
 * <p>Calcula la metrica a partir de las obligaciones del estado de cuenta que devuelve SAMNET
 * (SamnetGateway, ps_ctacte). Cuando el ETL cargue el modelo estrella, el valor tambien queda en
 * analitica.dim_contribuyente.porcentaje_pago_a_tiempo como insumo de la segmentacion.</p>
 */
@Service
public class RegularidadPagoService {

    private final SamnetGateway samnet;
    private final ContribuyenteService contribuyenteService;
    private final Clock reloj;

    public RegularidadPagoService(SamnetGateway samnet, ContribuyenteService contribuyenteService, Clock reloj) {
        this.samnet = samnet;
        this.contribuyenteService = contribuyenteService;
        this.reloj = reloj;
    }

    /**
     * Calcula la regularidad de pago de un contribuyente para el anio fiscal especificado.
     * Si no se indica anio, se toma el anio en curso segun el reloj del sistema.
     *
     * @param codigo codigo unico del contribuyente (ej. "014502")
     * @param anio anio fiscal a evaluar (opcional)
     * @return regularidad calculada con porcentaje y clasificacion para segmentacion
     */
    public RegularidadPagoRespuesta calcularRegularidad(String codigo, Integer anio) {
        Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(codigo);

        int anioEvaluado = (anio != null) ? anio : LocalDate.now(reloj).getYear();

        List<Obligacion> cuotas = samnet.obtenerEstadoCuenta(codigo).desglosar(null, anioEvaluado);
        int totalCuotas = cuotas.size();

        if (totalCuotas == 0) {
            return new RegularidadPagoRespuesta(
                    contribuyente.getCodigoContribuyente(),
                    contribuyente.getNombreRazonSocial(),
                    anioEvaluado,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO.setScale(2),
                    "SIN_EMISIONES",
                    "No se registran cuotas emitidas para el contribuyente en el anio " + anioEvaluado + "."
            );
        }

        // Una cuota se considera pagada a tiempo si su saldo es 0 (montoPagado >= montoEmitido)
        int cuotasPagadas = (int) cuotas.stream()
                .filter(o -> o.saldo().signum() <= 0)
                .count();

        int cuotasPendientesOVencidas = totalCuotas - cuotasPagadas;

        BigDecimal porcentaje = BigDecimal.valueOf((double) cuotasPagadas / totalCuotas * 100)
                .setScale(2, RoundingMode.HALF_UP);

        String segmentoSugerido;
        if (porcentaje.compareTo(BigDecimal.valueOf(100)) >= 0) {
            segmentoSugerido = "PUNTUAL";
        } else if (porcentaje.compareTo(BigDecimal.valueOf(75)) >= 0) {
            segmentoSugerido = "REGULAR";
        } else if (porcentaje.compareTo(BigDecimal.valueOf(50)) >= 0) {
            segmentoSugerido = "IRREGULAR";
        } else {
            segmentoSugerido = "MOROSO";
        }

        String observacion = String.format("%d de %d cuotas pagadas a tiempo (%s%%).",
                cuotasPagadas, totalCuotas, porcentaje);

        return new RegularidadPagoRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                anioEvaluado,
                totalCuotas,
                cuotasPagadas,
                cuotasPendientesOVencidas,
                porcentaje,
                segmentoSugerido,
                observacion
        );
    }
}
