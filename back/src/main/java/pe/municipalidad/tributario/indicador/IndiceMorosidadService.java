package pe.municipalidad.tributario.indicador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.analitica.HechoCobranzaRepository;
import pe.municipalidad.tributario.analitica.MontosMorosidad;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.indicador.CalculoMorosidadRespuesta.DetalleMorosidad;
import pe.municipalidad.tributario.indicador.IndiceMorosidadRespuesta.ValorPorAlcance;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU008 (RF-009): en cada carga del ETL, índice de morosidad = (monto de cuotas vencidas no pagadas / monto
 * emitido) x 100, a nivel total, por tributo y por zona. Se toman las cuotas del año de la fecha de corte que ya
 * vencieron (exigibles) y el valor se guarda en el mes de la fecha de corte, el mismo periodo de las metas.
 */
@Service
@Transactional(readOnly = true)
public class IndiceMorosidadService {

    public static final String CODIGO_INDICADOR = "INDICE_MOROSIDAD";

    private static final int ESCALA_INDICE = 4;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final LoteETLRepository loteETLRepository;
    private final HechoCobranzaRepository hechoCobranzaRepository;
    private final IndicadorRepository indicadorRepository;
    private final ValorIndicadorRepository valorIndicadorRepository;
    private final AlertaDesvioService alertaDesvioService;

    public IndiceMorosidadService(LoteETLRepository loteETLRepository,
            HechoCobranzaRepository hechoCobranzaRepository, IndicadorRepository indicadorRepository,
            ValorIndicadorRepository valorIndicadorRepository, AlertaDesvioService alertaDesvioService) {
        this.loteETLRepository = loteETLRepository;
        this.hechoCobranzaRepository = hechoCobranzaRepository;
        this.indicadorRepository = indicadorRepository;
        this.valorIndicadorRepository = valorIndicadorRepository;
        this.alertaDesvioService = alertaDesvioService;
    }

    @Transactional
    public CalculoMorosidadRespuesta calcularParaLote(Long idLote) {
        LoteETL lote = loteETLRepository.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."));
        if (lote.getEstado() == EstadoLote.RECHAZADO) {
            throw new ReglaNegocioException("LOTE_RECHAZADO",
                    "El lote " + idLote + " fue rechazado: sus datos no se usan para los indicadores.");
        }
        Indicador indicador = indicadorRepository.findById(CODIGO_INDICADOR)
                .orElseThrow(() -> new RecursoNoEncontradoException("INDICADOR_NO_ENCONTRADO",
                        "No existe el indicador " + CODIGO_INDICADOR + "."));

        LocalDate fechaCorte = lote.getFechaCorte();
        int anio = fechaCorte.getYear();
        int numeroPeriodo = fechaCorte.getMonthValue();
        List<MontosMorosidad> grupos = hechoCobranzaRepository.sumarCuotasExigibles(idLote, anio, fechaCorte);
        if (grupos.isEmpty()) {
            throw new ReglaNegocioException("SIN_CUOTAS_EXIGIBLES",
                    "El lote " + idLote + " no tiene cuotas de " + anio + " vencidas al " + fechaCorte
                            + ": no se puede calcular el índice de morosidad.");
        }

        Map<ClaveValor, ValorIndicador> existentes = valorIndicadorRepository.buscarPorLote(idLote).stream()
                .filter(valor -> valor.getIndicador().getCodigo().equals(CODIGO_INDICADOR))
                .collect(Collectors.toMap(ClaveValor::de, Function.identity()));
        Calculo calculo = new Calculo(indicador, lote, anio, numeroPeriodo, existentes);

        Montos montosTotales = Montos.CERO;
        Map<Tributo, Montos> porTributo = new EnumMap<>(Tributo.class);
        Map<String, Montos> porZona = new TreeMap<>();
        for (MontosMorosidad grupo : grupos) {
            Montos montos = new Montos(grupo.montoEmitido(), grupo.montoVencidoNoPagado());
            montosTotales = montosTotales.sumar(montos);
            porTributo.merge(grupo.tributo(), montos, Montos::sumar);
            porZona.merge(grupo.zona(), montos, Montos::sumar);
        }

        DetalleMorosidad total = calculo.registrar(AlcanceIndicador.TOTAL, null, montosTotales).orElse(null);
        List<DetalleMorosidad> detallePorTributo = new ArrayList<>();
        porTributo.forEach((tributo, montos) -> calculo.registrar(AlcanceIndicador.TRIBUTO, tributo.name(), montos)
                .ifPresent(detallePorTributo::add));
        List<DetalleMorosidad> detallePorZona = new ArrayList<>();
        porZona.forEach((zona, montos) -> calculo.registrar(AlcanceIndicador.ZONA, zona, montos)
                .ifPresent(detallePorZona::add));

        /* En un lote ya publicado el valor nuevo afecta las alertas; uno en curso se evalúa al publicarse. */
        EvaluacionLoteRespuesta evaluacion = lote.getEstado() == EstadoLote.PUBLICADO
                ? alertaDesvioService.evaluarLote(idLote)
                : null;
        return new CalculoMorosidadRespuesta(lote.getIdLote(), fechaCorte, anio, numeroPeriodo,
                indicador.getPeriodicidad().etiqueta(anio, numeroPeriodo), total, detallePorTributo,
                detallePorZona, evaluacion);
    }

    /* Sin año ni periodo, devuelve el índice de la última carga publicada. */
    public IndiceMorosidadRespuesta obtenerUltimo(Integer anio, Integer numeroPeriodo) {
        List<ValorIndicador> valores =
                valorIndicadorRepository.buscarPublicadosDeIndicador(CODIGO_INDICADOR, anio, numeroPeriodo);
        if (valores.isEmpty()) {
            throw new RecursoNoEncontradoException("MOROSIDAD_NO_CALCULADA",
                    "Aún no hay un índice de morosidad calculado en una carga publicada para el periodo pedido.");
        }
        ValorIndicador referencia = valores.getFirst();
        List<ValorIndicador> delPeriodo = valores.stream()
                .filter(valor -> valor.getLote().getIdLote().equals(referencia.getLote().getIdLote())
                        && valor.getAnio() == referencia.getAnio()
                        && valor.getNumeroPeriodo() == referencia.getNumeroPeriodo())
                .toList();

        BigDecimal total = delPeriodo.stream()
                .filter(valor -> valor.getAlcance() == AlcanceIndicador.TOTAL)
                .map(ValorIndicador::getValor)
                .findFirst()
                .orElse(null);
        return new IndiceMorosidadRespuesta(referencia.getLote().getIdLote(), referencia.getFechaCorte(),
                referencia.getAnio(), referencia.getNumeroPeriodo(), referencia.etiquetaPeriodo(), total,
                valoresDe(delPeriodo, AlcanceIndicador.TRIBUTO), valoresDe(delPeriodo, AlcanceIndicador.ZONA));
    }

    private static List<ValorPorAlcance> valoresDe(List<ValorIndicador> valores, AlcanceIndicador alcance) {
        return valores.stream()
                .filter(valor -> valor.getAlcance() == alcance)
                .map(valor -> new ValorPorAlcance(valor.getValorAlcance(), valor.getValor()))
                .sorted((a, b) -> a.valorAlcance().compareTo(b.valorAlcance()))
                .toList();
    }

    /* Guarda o, si el lote ya se calculó, actualiza cada valor del índice. */
    private final class Calculo {

        private final Indicador indicador;
        private final LoteETL lote;
        private final int anio;
        private final int numeroPeriodo;
        private final Map<ClaveValor, ValorIndicador> existentes;

        private Calculo(Indicador indicador, LoteETL lote, int anio, int numeroPeriodo,
                Map<ClaveValor, ValorIndicador> existentes) {
            this.indicador = indicador;
            this.lote = lote;
            this.anio = anio;
            this.numeroPeriodo = numeroPeriodo;
            this.existentes = existentes;
        }

        /* Sin monto emitido el índice no está definido y no se registra. */
        private Optional<DetalleMorosidad> registrar(AlcanceIndicador alcance, String valorAlcance, Montos montos) {
            if (montos.emitido().signum() == 0) {
                return Optional.empty();
            }
            BigDecimal indice = montos.vencidoNoPagado().multiply(CIEN)
                    .divide(montos.emitido(), ESCALA_INDICE, RoundingMode.HALF_UP);
            ValorIndicador existente = existentes.get(new ClaveValor(anio, numeroPeriodo, alcance, valorAlcance));
            if (existente != null) {
                existente.recalcular(indice);
            } else {
                valorIndicadorRepository.save(new ValorIndicador(indicador, lote, anio, numeroPeriodo, alcance,
                        valorAlcance, indice));
            }
            return Optional.of(new DetalleMorosidad(alcance, valorAlcance, montos.emitido(),
                    montos.vencidoNoPagado(), indice));
        }
    }

    private record ClaveValor(int anio, int numeroPeriodo, AlcanceIndicador alcance, String valorAlcance) {

        static ClaveValor de(ValorIndicador valor) {
            return new ClaveValor(valor.getAnio(), valor.getNumeroPeriodo(), valor.getAlcance(),
                    valor.getValorAlcance());
        }
    }

    private record Montos(BigDecimal emitido, BigDecimal vencidoNoPagado) {

        static final Montos CERO = new Montos(BigDecimal.ZERO, BigDecimal.ZERO);

        Montos {
            emitido = Objects.requireNonNullElse(emitido, BigDecimal.ZERO);
            vencidoNoPagado = Objects.requireNonNullElse(vencidoNoPagado, BigDecimal.ZERO);
        }

        Montos sumar(Montos otros) {
            return new Montos(emitido.add(otros.emitido), vencidoNoPagado.add(otros.vencidoNoPagado));
        }
    }
}
