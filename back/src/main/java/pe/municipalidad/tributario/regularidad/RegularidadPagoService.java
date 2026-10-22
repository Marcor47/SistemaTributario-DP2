package pe.municipalidad.tributario.regularidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteRepository;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.ObligacionRepository;

/**
 * HU010 - Logica de negocio para calcular la regularidad de pago del contribuyente.
 *
 * <p>Hoy calcula la metrica directamente a partir de las obligaciones registradas
 * en la tabla tributario.obligacion. Cuando el equipo de BD entregue el Stored Procedure
 * correspondiente (ej. sp_calcular_regularidad_contribuyente), se invocara aqui
 * directamente sin alterar el contrato del controlador ni la integracion con el frontend.</p>
 */
@Service
@Transactional(readOnly = true)
public class RegularidadPagoService {

    private final ContribuyenteRepository contribuyenteRepository;
    private final ObligacionRepository obligacionRepository;
    private final Clock reloj;

    public RegularidadPagoService(ContribuyenteRepository contribuyenteRepository,
            ObligacionRepository obligacionRepository, Clock reloj) {
        this.contribuyenteRepository = contribuyenteRepository;
        this.obligacionRepository = obligacionRepository;
        this.reloj = reloj;
    }

    /**
     * Calcula la regularidad de pago de un contribuyente para el anio fiscal especificado.
     * Si no se indica anio, se toma el anio en curso segun el reloj del sistema.
     *
     * @param codigo codigo unico del contribuyente (ej. "014502")
     * @param anio anio fiscal a evaluar (opcional)
     * @return regularidad calculada con porcentaje y clasificacion para segmentacion
     * @throws RecursoNoEncontradoException si el contribuyente no existe
     */
    public RegularidadPagoRespuesta calcularRegularidad(String codigo, Integer anio) {
        Contribuyente contribuyente = contribuyenteRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con codigo " + codigo + "."));

        int anioEvaluado = (anio != null) ? anio : LocalDate.now(reloj).getYear();

        List<Obligacion> cuotas = obligacionRepository.findAllByContribuyenteCodigoAndAnio(codigo, anioEvaluado);
        int totalCuotas = cuotas.size();

        if (totalCuotas == 0) {
            return new RegularidadPagoRespuesta(
                    contribuyente.getCodigo(),
                    contribuyente.getNombre(),
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
                contribuyente.getCodigo(),
                contribuyente.getNombre(),
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
