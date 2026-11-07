package pe.municipalidad.tributario.regularidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.regularidad.RegularidadPagoRespuesta.CuotaRegularidad;

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

        LocalDate fechaCorte = LocalDate.now(reloj);
        EstadoCuenta estadoCuenta = samnet.obtenerEstadoCuenta(codigo);
        List<Obligacion> cuotas = estadoCuenta.desglosar(null, anioEvaluado);
        List<Integer> aniosDisponibles = estadoCuenta.getObligaciones().stream()
                .map(Obligacion::getAnio).distinct().sorted(Comparator.reverseOrder()).toList();
        List<CuotaRegularidad> detalle = cuotas.stream()
                .sorted(Comparator.comparing(Obligacion::getFechaVencimiento)
                        .thenComparing(o -> o.getTributo().name()))
                .map(o -> cuota(o, fechaCorte))
                .toList();
        int totalCuotas = cuotas.size();
        /* Solo cuentan las cuotas que ya vencieron: una que aún no vence no es incumplimiento. */
        List<Obligacion> exigibles = cuotas.stream()
                .filter(o -> o.getFechaVencimiento().isBefore(fechaCorte))
                .toList();

        if (exigibles.isEmpty()) {
            return new RegularidadPagoRespuesta(
                    contribuyente.getCodigoContribuyente(),
                    contribuyente.getNombreRazonSocial(),
                    anioEvaluado,
                    totalCuotas,
                    0,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO.setScale(2),
                    "SIN_EMISIONES",
                    totalCuotas == 0
                            ? "No se registran cuotas emitidas para el contribuyente en el anio " + anioEvaluado + "."
                            : "Ninguna cuota del anio " + anioEvaluado + " ha vencido todavia.",
                    fechaCorte, aniosDisponibles, detalle
            );
        }

        /* A tiempo: cancelada sin pagos posteriores a su vencimiento (Obligacion.pagadaATiempo). */
        int cuotasPagadas = (int) exigibles.stream().filter(Obligacion::pagadaATiempo).count();
        int cuotasConAtraso = (int) exigibles.stream()
                .filter(o -> o.saldo().signum() <= 0 && !o.pagadaATiempo())
                .count();
        int cuotasPendientesOVencidas = exigibles.size() - cuotasPagadas - cuotasConAtraso;

        BigDecimal porcentaje = BigDecimal.valueOf(cuotasPagadas * 100L)
                .divide(BigDecimal.valueOf(exigibles.size()), 2, RoundingMode.HALF_UP);

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

        String observacion = String.format(
                "%d de %d cuotas exigibles pagadas a tiempo (%s%%); %d pagadas con atraso y %d impagas.",
                cuotasPagadas, exigibles.size(), porcentaje, cuotasConAtraso, cuotasPendientesOVencidas);

        return new RegularidadPagoRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                anioEvaluado,
                totalCuotas,
                exigibles.size(),
                cuotasPagadas,
                cuotasConAtraso,
                cuotasPendientesOVencidas,
                porcentaje,
                segmentoSugerido,
                observacion,
                fechaCorte,
                aniosDisponibles,
                detalle
        );
    }

    private static CuotaRegularidad cuota(Obligacion obligacion, LocalDate fechaCorte) {
        String estado;
        if (obligacion.saldo().signum() <= 0) {
            estado = obligacion.pagadaATiempo() ? "A_TIEMPO" : "CON_ATRASO";
        } else {
            estado = obligacion.getFechaVencimiento().isBefore(fechaCorte) ? "VENCIDA_SIN_PAGAR" : "POR_VENCER";
        }
        return new CuotaRegularidad(obligacion.getIdObligacion(), obligacion.etiquetaPeriodo(),
                obligacion.getTributo().name(), obligacion.getCodigoPredio(), obligacion.getFechaVencimiento(),
                obligacion.getFechaUltimoPago(), obligacion.getMontoEmitido(), estado);
    }
}
