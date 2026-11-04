package pe.municipalidad.tributario.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.ia.EstadoSegmento;
import pe.municipalidad.tributario.ia.Segmento;
import pe.municipalidad.tributario.ia.SegmentoRepository;
import pe.municipalidad.tributario.ia.SegmentacionService;
import pe.municipalidad.tributario.indicador.AlcanceIndicador;
import pe.municipalidad.tributario.indicador.Indicador;
import pe.municipalidad.tributario.indicador.IndiceMorosidadService;
import pe.municipalidad.tributario.indicador.MetaIndicador;
import pe.municipalidad.tributario.indicador.MetaIndicadorRepository;
import pe.municipalidad.tributario.indicador.SentidoIndicador;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU011 (RF-012): dashboard ejecutivo de Rentas sobre la última carga publicada del ETL. Muestra la recaudación
 * frente a la emisión, el avance frente a las metas, el índice de morosidad, la recaudación por tributo y la
 * distribución por segmento. Se calcula al consultar para que cualquier combinación de filtros (periodo, zona,
 * segmento, tributo) dé cifras coherentes entre sí; las fórmulas son las del catálogo de indicadores (V3).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final String INDICADOR_RECAUDACION_EMISION = "PORC_RECAUDACION_EMISION";
    static final String INDICADOR_MONTO_RECAUDADO = "MONTO_RECAUDADO";

    private static final int ESCALA_PORCENTAJE = 4;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final int ULTIMO_MES = 12;

    private final DashboardRepository dashboardRepository;
    private final SegmentoRepository segmentoRepository;
    private final SegmentacionService segmentacionService;
    private final MetaIndicadorRepository metaIndicadorRepository;

    public DashboardService(DashboardRepository dashboardRepository, SegmentoRepository segmentoRepository,
            SegmentacionService segmentacionService, MetaIndicadorRepository metaIndicadorRepository) {
        this.dashboardRepository = dashboardRepository;
        this.segmentoRepository = segmentoRepository;
        this.segmentacionService = segmentacionService;
        this.metaIndicadorRepository = metaIndicadorRepository;
    }

    /*
     * Sin periodo se muestra el mes de la fecha de corte de la carga. Con un periodo anterior se toman las cuotas
     * que vencieron hasta el fin de ese mes; su estado de pago es el de la fecha de corte de la carga.
     */
    public DashboardRespuesta consultar(Integer anio, Integer numeroPeriodo, String zona, Tributo tributo,
            Long idSegmento) {
        LoteETL lote = ultimaCargaPublicada();
        LocalDate corteCarga = lote.getFechaCorte();
        int anioConsulta = anio == null ? corteCarga.getYear() : anio;
        int periodoConsulta = numeroPeriodo != null ? numeroPeriodo
                : anioConsulta == corteCarga.getYear() ? corteCarga.getMonthValue() : ULTIMO_MES;
        LocalDate finDelPeriodo = YearMonth.of(anioConsulta, periodoConsulta).plusMonths(1).atDay(1);
        LocalDate fechaCorte = finDelPeriodo.isBefore(corteCarga) ? finDelPeriodo : corteCarga;

        String zonaFiltro = zona == null || zona.isBlank() ? null : zona.strip();
        Segmento segmento = idSegmento == null ? null : segmentoPublicado(idSegmento);

        List<MontosDashboard> grupos = dashboardRepository.sumarPorTributoYZona(lote.getIdLote(), anioConsulta,
                fechaCorte, zonaFiltro, tributo, idSegmento);
        Totales total = Totales.CERO;
        Map<Tributo, Totales> porTributo = new EnumMap<>(Tributo.class);
        Map<String, Totales> porZona = new TreeMap<>();
        for (MontosDashboard grupo : grupos) {
            Totales montos = new Totales(grupo.montoEmitido(), grupo.montoRecaudado(), grupo.montoVencidoNoPagado());
            total = total.sumar(montos);
            porTributo.merge(grupo.tributo(), montos, Totales::sumar);
            porZona.merge(grupo.zona(), montos, Totales::sumar);
        }
        Totales totalFinal = total;

        List<DashboardRespuesta.PorTributo> detalleTributo = porTributo.entrySet().stream()
                .map(e -> new DashboardRespuesta.PorTributo(e.getKey(), e.getKey().getDescripcion(),
                        e.getValue().emitido(), e.getValue().recaudado(), e.getValue().porcentajeRecaudacion(),
                        porcentaje(e.getValue().recaudado(), totalFinal.recaudado()),
                        e.getValue().indiceMorosidad()))
                .toList();
        List<DashboardRespuesta.PorZona> detalleZona = porZona.entrySet().stream()
                .map(e -> new DashboardRespuesta.PorZona(e.getKey(), e.getValue().emitido(),
                        e.getValue().recaudado(), e.getValue().porcentajeRecaudacion(),
                        e.getValue().indiceMorosidad()))
                .toList();
        List<DashboardRespuesta.PorSegmento> detalleSegmento = dashboardRepository.sumarPorSegmento(
                        lote.getIdLote(), anioConsulta, fechaCorte, zonaFiltro, tributo, idSegmento).stream()
                .map(DashboardService::porSegmento)
                .toList();

        DashboardRespuesta.Resumen resumen = new DashboardRespuesta.Resumen(total.emitido(), total.recaudado(),
                total.porcentajeRecaudacion(), total.vencidoNoPagado(), total.indiceMorosidad());
        AlcanceFiltro alcance = AlcanceFiltro.de(zonaFiltro, tributo, segmento);
        return new DashboardRespuesta(lote.getIdLote(), corteCarga, fechaCorte, anioConsulta, periodoConsulta,
                String.format("%d-%02d", anioConsulta, periodoConsulta),
                new DashboardRespuesta.FiltrosAplicados(zonaFiltro, tributo, idSegmento,
                        segmento == null ? null : segmento.getNombre()),
                resumen, avanceMetas(anioConsulta, periodoConsulta, alcance, resumen), detalleTributo, detalleZona,
                detalleSegmento);
    }

    public DashboardFiltrosRespuesta filtros() {
        LoteETL lote = ultimaCargaPublicada();
        return new DashboardFiltrosRespuesta(lote.getIdLote(), lote.getFechaCorte(),
                dashboardRepository.listarAnios(lote.getIdLote()),
                dashboardRepository.listarZonas(lote.getIdLote()),
                Arrays.stream(Tributo.values())
                        .map(t -> new DashboardFiltrosRespuesta.OpcionTributo(t.name(), t.getDescripcion()))
                        .toList(),
                segmentacionService.listarPublicados());
    }

    private LoteETL ultimaCargaPublicada() {
        return dashboardRepository.buscarCargasPublicadasConHechos(PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("SIN_CARGA_ETL",
                        "Aún no hay una carga publicada del ETL: el dashboard no tiene datos que mostrar."));
    }

    /* Solo un segmento publicado por la jefatura es un filtro válido (HU031). */
    private Segmento segmentoPublicado(Long idSegmento) {
        Segmento segmento = segmentoRepository.findById(idSegmento)
                .orElseThrow(() -> new RecursoNoEncontradoException("SEGMENTO_NO_ENCONTRADO",
                        "No existe el segmento " + idSegmento + "."));
        if (segmento.getEstado() != EstadoSegmento.PUBLICADO) {
            throw new ReglaNegocioException("SEGMENTO_NO_PUBLICADO",
                    "El segmento " + idSegmento + " no está publicado en el dashboard.");
        }
        return segmento;
    }

    private List<DashboardRespuesta.AvanceMeta> avanceMetas(int anio, int numeroPeriodo, AlcanceFiltro alcance,
            DashboardRespuesta.Resumen resumen) {
        if (alcance == null) {
            return List.of();
        }
        List<DashboardRespuesta.AvanceMeta> avances = new ArrayList<>();
        Stream.of(
                        new Observado(INDICADOR_RECAUDACION_EMISION, resumen.porcentajeRecaudacion()),
                        new Observado(IndiceMorosidadService.CODIGO_INDICADOR, resumen.indiceMorosidad()),
                        new Observado(INDICADOR_MONTO_RECAUDADO, resumen.montoRecaudado()))
                .forEach(observado -> metaIndicadorRepository
                        .findByIndicadorCodigoAndAnioAndNumeroPeriodo(observado.codigo(), anio, numeroPeriodo)
                        .filter(alcance::coincide)
                        .map(meta -> avance(meta, observado.valor()))
                        .ifPresent(avances::add));
        return avances;
    }

    private static DashboardRespuesta.AvanceMeta avance(MetaIndicador meta, BigDecimal observado) {
        Indicador indicador = meta.getIndicador();
        BigDecimal valorMeta = meta.getValorMeta();
        Boolean cumple = observado == null ? null
                : indicador.getSentido() == SentidoIndicador.ASCENDENTE
                        ? observado.compareTo(valorMeta) >= 0
                        : observado.compareTo(valorMeta) <= 0;
        BigDecimal avance = observado == null ? null : porcentaje(observado, valorMeta);
        return new DashboardRespuesta.AvanceMeta(indicador.getCodigo(), indicador.getNombre(),
                indicador.getUnidadMedida(), indicador.getSentido(), meta.getAlcance(), meta.getValorAlcance(),
                valorMeta, observado, avance, cumple);
    }

    private static DashboardRespuesta.PorSegmento porSegmento(MontosSegmento montos) {
        Totales totales = new Totales(montos.montoEmitido(), montos.montoRecaudado(), montos.montoVencidoNoPagado());
        return new DashboardRespuesta.PorSegmento(montos.idSegmento(), montos.nombre(), montos.contribuyentes(),
                totales.emitido(), totales.recaudado(), totales.porcentajeRecaudacion(), totales.indiceMorosidad());
    }

    /* Nulo cuando la base es 0: el porcentaje no está definido. */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return parte.multiply(CIEN).divide(base, ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }

    private record Observado(String codigo, BigDecimal valor) {
    }

    private record Totales(BigDecimal emitido, BigDecimal recaudado, BigDecimal vencidoNoPagado) {

        static final Totales CERO = new Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        Totales sumar(Totales otros) {
            return new Totales(emitido.add(otros.emitido), recaudado.add(otros.recaudado),
                    vencidoNoPagado.add(otros.vencidoNoPagado));
        }

        BigDecimal porcentajeRecaudacion() {
            return porcentaje(recaudado, emitido);
        }

        BigDecimal indiceMorosidad() {
            return porcentaje(vencidoNoPagado, emitido);
        }
    }

    /*
     * Alcance de meta que corresponde a los filtros. Con dos o más filtros no hay meta comparable (de() devuelve
     * nulo). La meta de un segmento puede registrarse con su id o con su nombre.
     */
    private record AlcanceFiltro(AlcanceIndicador alcance, List<String> valoresAceptados) {

        static AlcanceFiltro de(String zona, Tributo tributo, Segmento segmento) {
            long filtros = Stream.of(zona, tributo, segmento).filter(valor -> valor != null).count();
            if (filtros > 1) {
                return null;
            }
            if (zona != null) {
                return new AlcanceFiltro(AlcanceIndicador.ZONA, List.of(zona));
            }
            if (tributo != null) {
                return new AlcanceFiltro(AlcanceIndicador.TRIBUTO, List.of(tributo.name()));
            }
            if (segmento != null) {
                return new AlcanceFiltro(AlcanceIndicador.SEGMENTO,
                        List.of(segmento.getIdSegmento().toString(), segmento.getNombre()));
            }
            return new AlcanceFiltro(AlcanceIndicador.TOTAL, List.of());
        }

        boolean coincide(MetaIndicador meta) {
            if (meta.getAlcance() != alcance) {
                return false;
            }
            return alcance == AlcanceIndicador.TOTAL || Optional.ofNullable(meta.getValorAlcance())
                    .map(valor -> valoresAceptados.stream().anyMatch(valor::equalsIgnoreCase))
                    .orElse(false);
        }
    }
}
