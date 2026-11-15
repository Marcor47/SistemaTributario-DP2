package pe.municipalidad.tributario.regularidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.ia.CategoriaRiesgo;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;

/*
 * HU021 (RF-035): clasificación del riesgo de mora de un contribuyente. Un modelo logístico sobre su historial de
 * los últimos tres años estima la probabilidad de que deje vencer sus próximas cuotas: qué parte de sus cuotas
 * vencidas sigue impaga, qué parte pagó con atraso y cuántos días de atraso promedia. La categoría (bajo, medio o
 * alto) orienta la cobranza preventiva; los factores explican la clasificación a quien la consulta.
 */
@Service
public class RiesgoMoraService {

    static final String MODELO = "Regresión logística sobre el historial de pagos (3 años)";
    private static final int ANIOS_HISTORIA = 3;
    private static final int TOPE_DIAS = 365;
    private static final double INTERCEPTO = -2.4;
    private static final double PESO_IMPAGO = 3.2;
    private static final double PESO_ATRASO = 1.6;
    private static final double PESO_DIAS = 0.004;
    private static final double UMBRAL_MEDIO = 0.30;
    private static final double UMBRAL_ALTO = 0.60;

    public record RiesgoMoraRespuesta(String codigoContribuyente, LocalDate fechaCorte, BigDecimal probabilidad,
            CategoriaRiesgo categoria, int cuotasEvaluadas, int cuotasImpagas, int cuotasConAtraso,
            long diasAtrasoPromedio, BigDecimal deudaVencida, List<String> factores, String modelo) {
    }

    private final SamnetGateway samnet;
    private final ContribuyenteService contribuyentes;

    public RiesgoMoraService(SamnetGateway samnet, ContribuyenteService contribuyentes) {
        this.samnet = samnet;
        this.contribuyentes = contribuyentes;
    }

    public RiesgoMoraRespuesta calcular(String codigo) {
        contribuyentes.obtenerPorCodigo(codigo);
        EstadoCuenta cuenta = samnet.obtenerEstadoCuenta(codigo);
        LocalDate corte = cuenta.obtenerFechaCorte();
        LocalDate desde = corte.minusYears(ANIOS_HISTORIA);
        List<Obligacion> vencidas = cuenta.getObligaciones().stream()
                .filter(o -> o.getFechaVencimiento().isBefore(corte) && !o.getFechaVencimiento().isBefore(desde))
                .toList();
        if (vencidas.isEmpty()) {
            throw new RecursoNoEncontradoException("SIN_HISTORIAL",
                    "El contribuyente no tiene cuotas vencidas en los últimos tres años para estimar su riesgo.");
        }
        int impagas = 0;
        int conAtraso = 0;
        long diasAtraso = 0;
        BigDecimal deuda = BigDecimal.ZERO;
        for (Obligacion o : vencidas) {
            if (o.saldo().signum() > 0) {
                impagas++;
                deuda = deuda.add(o.saldo());
                diasAtraso += Math.min(TOPE_DIAS, o.diasVencidaAl(corte));
            } else if (!o.pagadaATiempo() && o.getFechaUltimoPago() != null) {
                conAtraso++;
                diasAtraso += Math.min(TOPE_DIAS,
                        ChronoUnit.DAYS.between(o.getFechaVencimiento(), o.getFechaUltimoPago()));
            }
        }
        int total = vencidas.size();
        double proporcionImpaga = (double) impagas / total;
        double proporcionAtraso = (double) conAtraso / total;
        long promedio = impagas + conAtraso == 0 ? 0 : diasAtraso / (impagas + conAtraso);
        double z = INTERCEPTO + PESO_IMPAGO * proporcionImpaga + PESO_ATRASO * proporcionAtraso + PESO_DIAS * promedio;
        double probabilidad = 1 / (1 + Math.exp(-z));
        CategoriaRiesgo categoria = probabilidad >= UMBRAL_ALTO ? CategoriaRiesgo.ALTO
                : probabilidad >= UMBRAL_MEDIO ? CategoriaRiesgo.MEDIO : CategoriaRiesgo.BAJO;

        List<String> factores = new ArrayList<>();
        factores.add(impagas == 0 ? "No tiene cuotas vencidas impagas."
                : impagas + " de " + total + " cuotas vencidas siguen impagas (deuda vencida de S/ "
                        + deuda.setScale(2, RoundingMode.HALF_UP) + ").");
        factores.add(conAtraso == 0 ? "Pagó a tiempo las cuotas que canceló."
                : conAtraso + " cuotas se pagaron después de su vencimiento.");
        if (promedio > 0) {
            factores.add("Atraso promedio de " + promedio + " días entre las cuotas atrasadas o impagas.");
        }
        return new RiesgoMoraRespuesta(codigo, corte,
                BigDecimal.valueOf(probabilidad).setScale(4, RoundingMode.HALF_UP), categoria, total, impagas,
                conAtraso, promedio, deuda.setScale(2, RoundingMode.HALF_UP), factores, MODELO);
    }
}
