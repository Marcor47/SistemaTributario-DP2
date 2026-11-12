package pe.municipalidad.tributario.reporteria;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.indicador.AlcanceIndicador;
import pe.municipalidad.tributario.indicador.MetaIndicador;
import pe.municipalidad.tributario.indicador.MetaIndicadorRepository;
import pe.municipalidad.tributario.reporteria.CatalogoReporteriaRespuesta.Opcion;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Agregado;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Carga;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Filtro;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Grupo;
import pe.municipalidad.tributario.reporteria.DatosReporteriaRespuesta.Destacado;
import pe.municipalidad.tributario.reporteria.DatosReporteriaRespuesta.FilaContribuyente;

/* Consulta de la plataforma analítica de Rentas: catálogo de la carga, agregados, rankings y meta (RF-010, RF-012). */
@Service
@Transactional(readOnly = true)
public class ReporteriaService {

    private static final String INDICADOR_META = "PORC_RECAUDACION_EMISION";
    private static final int TAMANO_TOP = 10;
    private static final int MESES_MAXIMOS = 240;
    private static final int PUNTOS_PRICOS_MAXIMOS = 300;

    private final CuboReporteria cubo;
    private final MetaIndicadorRepository metas;

    public ReporteriaService(CuboReporteria cubo, MetaIndicadorRepository metas) {
        this.cubo = cubo;
        this.metas = metas;
    }

    public CatalogoReporteriaRespuesta catalogo() {
        Carga carga = cubo.carga();
        List<Opcion> zonas = carga.zonas().stream()
                .map(zona -> new Opcion(zona, CuboReporteria.SIN_ZONA.equals(zona) ? "Sin zona" : zona)).toList();
        List<Opcion> tributos = CuboReporteria.tributos().stream()
                .map(tributo -> new Opcion(tributo.name(), tributo.getDescripcion())).toList();
        List<Opcion> canales = CuboReporteria.CANALES.stream()
                .map(canal -> new Opcion(canal, "EN_LINEA".equals(canal) ? "En línea" : "Ventanilla")).toList();
        return new CatalogoReporteriaRespuesta(carga.fechaCorte(), carga.primerMes().toString(), carga.umbralPrico(),
                zonas, tributos, carga.segmentos(), carga.usos(), canales);
    }

    public DatosReporteriaRespuesta datos(Filtro filtro, Integer destacado) {
        validarPeriodo(filtro.desde(), filtro.hasta());
        Agregado a = cubo.calcular(filtro);
        Carga d = a.carga;
        Map<String, List<Integer>> rankings = rankings(a);
        Map<String, List<FilaContribuyente>> top = new LinkedHashMap<>();
        rankings.forEach((pestana, ids) -> top.put(pestana,
                ids.stream().limit(TAMANO_TOP).map(c -> fila(a, c)).toList()));
        return new DatosReporteriaRespuesta(
                filtro.desde().toString(), filtro.hasta().toString(),
                new DatosReporteriaRespuesta.Totales(soles(a.emitido), soles(a.cobrado), soles(a.recaudado),
                        soles(a.deuda), a.cuotas, a.cuotasATiempo, a.contribuyentes, a.morosos, a.buenosPagadores,
                        soles(a.recaudadoBuenosPagadores)),
                new DatosReporteriaRespuesta.Pricos(a.pricos, a.pricosMorosos, soles(a.pricosEmitido),
                        soles(a.pricosCobrado), soles(a.pricosDeuda)),
                meta(filtro),
                grupo(a.porMes),
                new DatosReporteriaRespuesta.ComparacionPorMes(soles(a.mesEmitidoPricos), soles(a.mesCobradoPricos),
                        soles(a.mesEmitidoResto), soles(a.mesCobradoResto)),
                soles(a.saldoPorSegmento),
                grupo(a.porZona),
                grupo(a.porTributo),
                grupo(a.porSegmento),
                soles(a.tributoEmitidoPricos),
                soles(a.zonaTributoEmitido),
                soles(a.zonaTributoDeuda),
                soles(a.antiguedad),
                enteros(a.canal),
                enteros(a.anticipacion),
                soles(a.usoEmitido),
                puntosPricos(a, d),
                top,
                destacado(a, rankings, destacado));
    }

    static void validarPeriodo(YearMonth desde, YearMonth hasta) {
        if (hasta.isBefore(desde)) {
            throw new DatoInvalidoException("PERIODO_INVALIDO", "El mes final no puede ser anterior al mes inicial.");
        }
        if (CuboReporteria.mes(hasta) - CuboReporteria.mes(desde) >= MESES_MAXIMOS) {
            throw new DatoInvalidoException("PERIODO_INVALIDO", "El periodo no puede superar los 20 años.");
        }
    }

    /* Criterio de cada pestaña; cada lista queda ordenada de mayor a menor por su monto principal. */
    private static Map<String, List<Integer>> rankings(Agregado a) {
        Carga d = a.carga;
        Map<String, List<Integer>> rankings = new LinkedHashMap<>();
        rankings.put("resumen", ranking(d.n(), c -> a.cRecaudado[c] > 0, c -> a.cRecaudado[c]));
        rankings.put("pricos", ranking(d.n(), c -> d.cp()[c] && a.cEmitido[c] > 0, c -> a.cEmitido[c]));
        rankings.put("morosos", ranking(d.n(), c -> a.cDeuda[c] > 0, c -> a.cDeuda[c]));
        rankings.put("buenos", ranking(d.n(), c -> a.cCuotas[c] > 0 && a.cATiempo[c] == a.cCuotas[c],
                c -> a.cRecaudado[c]));
        return rankings;
    }

    private static List<Integer> ranking(int n, IntPredicate elegible, IntToLongFunction monto) {
        return IntStream.range(0, n).filter(elegible).boxed()
                .sorted(Comparator.comparingLong((Integer c) -> monto.applyAsLong(c)).reversed()).toList();
    }

    private static FilaContribuyente fila(Agregado a, int c) {
        Carga d = a.carga;
        int nt = CuboReporteria.tributos().size();
        int principal = 0;
        for (int t = 1; t < nt; t++) {
            if (a.cDeudaTributo[c * nt + t] > a.cDeudaTributo[c * nt + principal]) {
                principal = t;
            }
        }
        String ultimoPago = a.cUltimoPago[c] < 0 ? null
                : YearMonth.of(a.cUltimoPago[c] / 12, a.cUltimoPago[c] % 12 + 1).toString();
        return new FilaContribuyente(c, d.codigo(c), d.nombre(c), d.cz()[c], d.cs()[c], d.cu()[c], d.cp()[c],
                d.juridica()[c], d.cpred()[c], soles(a.cEmitido[c]), soles(a.cCobrado[c]), soles(a.cRecaudado[c]),
                soles(a.cDeuda[c]), a.cCuotas[c], a.cATiempo[c], a.cVencidas[c], a.cAtraso[c], principal, ultimoPago);
    }

    /* Los PRICOS con mayor emisión del periodo: bastan para leer la relación y limitan el tamaño de la respuesta. */
    private static List<DatosReporteriaRespuesta.PuntoPrico> puntosPricos(Agregado a, Carga d) {
        return ranking(d.n(), c -> d.cp()[c] && a.cEmitido[c] > 0, c -> a.cEmitido[c]).stream()
                .limit(PUNTOS_PRICOS_MAXIMOS)
                .map(c -> new DatosReporteriaRespuesta.PuntoPrico(c, d.nombre(c), soles(a.cEmitido[c]),
                        Math.round(a.cCobrado[c] * 10000.0 / a.cEmitido[c]) / 100.0, soles(a.cDeuda[c]), d.cz()[c],
                        d.cs()[c], d.cu()[c]))
                .toList();
    }

    private static Destacado destacado(Agregado a, Map<String, List<Integer>> rankings, Integer c) {
        if (c == null || c < 0 || c >= a.carga.n() || (a.cCuotas[c] == 0 && a.cRecaudado[c] == 0)) {
            return null;
        }
        Map<String, Integer> puestos = new LinkedHashMap<>();
        rankings.forEach((pestana, ids) -> {
            int indice = ids.indexOf(c);
            if (indice >= 0) {
                puestos.put(pestana, indice + 1);
            }
        });
        return new Destacado(fila(a, c), puestos);
    }

    /*
     * Meta del indicador de recaudación en el último mes del periodo. Como en el dashboard, se compara solo con la meta
     * del alcance que corresponde a los filtros; con dos o más filtros no hay meta comparable.
     */
    private DatosReporteriaRespuesta.Meta meta(Filtro f) {
        long filtros = Arrays.asList(f.zona(), f.tributo(), f.segmento()).stream().filter(valor -> valor != null).count();
        if (filtros > 1) {
            return null;
        }
        return metas.findByIndicadorCodigoAndAnioAndNumeroPeriodo(INDICADOR_META, f.hasta().getYear(),
                        f.hasta().getMonthValue())
                .filter(meta -> coincideAlcance(meta, f))
                .map(meta -> new DatosReporteriaRespuesta.Meta(meta.getValorMeta(), meta.getUmbralDesvio(),
                        meta.getAnio(), meta.getNumeroPeriodo()))
                .orElse(null);
    }

    private static boolean coincideAlcance(MetaIndicador meta, Filtro f) {
        AlcanceIndicador alcance = f.zona() != null ? AlcanceIndicador.ZONA
                : f.tributo() != null ? AlcanceIndicador.TRIBUTO
                : f.segmento() != null ? AlcanceIndicador.SEGMENTO : AlcanceIndicador.TOTAL;
        String valor = f.zona() != null ? f.zona() : f.tributo() != null ? f.tributo() : f.segmento();
        return meta.getAlcance() == alcance && (alcance == AlcanceIndicador.TOTAL
                || Optional.ofNullable(meta.getValorAlcance()).filter(valor::equalsIgnoreCase).isPresent());
    }

    private static DatosReporteriaRespuesta.Grupo grupo(Grupo g) {
        return new DatosReporteriaRespuesta.Grupo(soles(g.emitido), soles(g.cobrado), soles(g.recaudado),
                soles(g.deuda), enteros(g.cuotas), enteros(g.cuotasATiempo), enteros(g.contribuyentes),
                enteros(g.morosos), enteros(g.buenosPagadores));
    }

    static double soles(long centimos) {
        return centimos / 100.0;
    }

    private static List<Double> soles(long[] centimos) {
        return Arrays.stream(centimos).mapToObj(ReporteriaService::soles).toList();
    }

    private static List<List<Double>> soles(long[][] centimos) {
        return Arrays.stream(centimos).map(ReporteriaService::soles).toList();
    }

    private static List<Integer> enteros(int[] valores) {
        return Arrays.stream(valores).boxed().toList();
    }
}
