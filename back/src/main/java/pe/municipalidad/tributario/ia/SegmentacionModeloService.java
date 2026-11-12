package pe.municipalidad.tributario.ia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import tools.jackson.databind.json.JsonMapper;

/*
 * HU030 (RF-043, D-32): segmentación no supervisada de los contribuyentes por su comportamiento de pago.
 *
 * Lee solo el oro (seudónimos, RNF-018) de la última carga del ETL con hechos y calcula cinco variables por
 * contribuyente; aplica logaritmo a las de cola larga, estandariza y prueba K-means con k = k-min..k-max. Se queda
 * con el k de mejor silueta cuyo segmento más chico tenga al menos tamano-minimo contribuyentes. Deja los segmentos
 * en BORRADOR con sus asignaciones: la jefatura los revisa y aprueba con /api/segmentos/aprobaciones (HU031) y
 * recién entonces se ven en el dashboard. Sin reglas ni etiquetas propias: el modelo no usa el segmento binario
 * actual, solo lo copia en la asignación para compararlos.
 *
 * Cada corrida registra el modelo (centros, escalado y parámetros, en JSON, con su SHA-256) y una TrazaIA
 * inalterable con la entrada y la salida (RNF-014). Los borradores de corridas anteriores que nadie aprobó se
 * reemplazan; lo aprobado o publicado no se toca. La misma carga con los mismos parámetros da el mismo modelo.
 *
 * La exploración que fijó las variables y el escalado está en scripts/segmentacion_explorar.py.
 */
@Service
public class SegmentacionModeloService {

    static final String NOMBRE_MODELO = "kmeans-comportamiento-pago";
    private static final String FUENTE_COBRANZA = "ps_etl_cobranza";
    private static final Logger LOG = LoggerFactory.getLogger(SegmentacionModeloService.class);

    /* Variables del modelo, en este orden. Los nombres son los de variables_caracteristicas. */
    static final List<String> VARIABLES = List.of("pctATiempo", "diasAtraso", "deudaVencidaPct",
            "antiguedadDeudaAnios", "mesesSinPago");
    private static final Map<String, String> UNIDADES = Map.of("pctATiempo", "%", "diasAtraso", "días",
            "deudaVencidaPct", "%", "antiguedadDeudaAnios", "años", "mesesSinPago", "meses");
    /* Frase para el nombre del segmento según el lado de la media en que cae su centro: {alto, bajo}. */
    private static final Map<String, String[]> FRASES = Map.of(
            "pctATiempo", new String[] {"paga a tiempo", "paga fuera de plazo"},
            "diasAtraso", new String[] {"atraso largo", "paga adelantado"},
            "deudaVencidaPct", new String[] {"deuda vencida alta", "sin deuda vencida"},
            "antiguedadDeudaAnios", new String[] {"deuda antigua", "sin deuda antigua"},
            "mesesSinPago", new String[] {"sin pagos recientes", "pagos recientes"});
    /* Para no repetir el mismo concepto en el nombre. */
    private static final Map<String, String> CONCEPTO = Map.of("pctATiempo", "plazo", "diasAtraso", "plazo",
            "deudaVencidaPct", "deuda", "antiguedadDeudaAnios", "deuda", "mesesSinPago", "pago");

    private static final String SQL_VARIABLES = """
            WITH q AS (
                SELECT h.seudonimo_contribuyente AS c, h.fecha_vencimiento AS venc, h.monto_emitido AS emitido,
                       h.monto_insoluto AS insoluto, h.estado_pago = 'PAGADA' AS pagada,
                       CASE WHEN h.estado_pago = 'PAGADA' THEN COALESCE(h.fecha_pago, CAST(? AS DATE))
                            ELSE CAST(? AS DATE) END AS fecha_efectiva,
                       h.fecha_vencimiento > CAST(? AS DATE) - make_interval(years => ?) AS en_ventana
                FROM analitica.hecho_cobranza h
                WHERE h.id_lote = ? AND h.fecha_vencimiento <= ?
            ), agregado AS (
                SELECT c,
                       avg(CASE WHEN pagada AND fecha_efectiva <= venc THEN 100.0 ELSE 0 END)
                           FILTER (WHERE en_ventana) AS pct_ventana,
                       avg(CASE WHEN pagada AND fecha_efectiva <= venc THEN 100.0 ELSE 0 END) AS pct_total,
                       avg(fecha_efectiva - venc) FILTER (WHERE en_ventana) AS atraso_ventana,
                       avg(fecha_efectiva - venc) AS atraso_total,
                       COALESCE(sum(insoluto) FILTER (WHERE NOT pagada), 0) * 100.0 / NULLIF(sum(emitido), 0)
                           AS deuda_pct,
                       min(venc) FILTER (WHERE NOT pagada) AS deuda_mas_antigua
                FROM q GROUP BY c
            ), pagos AS (
                SELECT seudonimo_contribuyente AS c, max(fecha_pago) AS ultimo, count(*) AS n,
                       count(*) FILTER (WHERE canal = 'EN_LINEA') AS en_linea
                FROM analitica.hecho_pago WHERE id_lote = ? AND fecha_pago <= ? GROUP BY 1
            ), estructura AS (
                SELECT seudonimo_contribuyente AS c, count(DISTINCT id_predio) AS predios,
                       COALESCE(sum(monto_emitido) FILTER (WHERE tributo = 'IMPUESTO_PREDIAL'), 0) * 100.0
                           / NULLIF(sum(monto_emitido), 0) AS pct_predial
                FROM analitica.hecho_cobranza WHERE id_lote = ? GROUP BY 1
            )
            SELECT a.c, d.segmento_binario_actual,
                   COALESCE(a.pct_ventana, a.pct_total) AS pct_a_tiempo,
                   COALESCE(a.atraso_ventana, a.atraso_total) AS dias_atraso,
                   COALESCE(a.deuda_pct, 0) AS deuda_pct,
                   COALESCE((CAST(? AS DATE) - a.deuda_mas_antigua) / 365.25, 0) AS antiguedad,
                   COALESCE((CAST(? AS DATE) - p.ultimo) / 30.44, ?) AS meses_sin_pago,
                   e.predios, COALESCE(e.pct_predial, 0) AS pct_predial,
                   COALESCE(p.en_linea * 100.0 / NULLIF(p.n, 0), 0) AS pct_en_linea
            FROM agregado a
            JOIN estructura e ON e.c = a.c
            JOIN analitica.dim_contribuyente d ON d.seudonimo_contribuyente = a.c
            LEFT JOIN pagos p ON p.c = a.c
            ORDER BY a.c
            """;

    private final JdbcTemplate jdbc;
    private final LoteETLRepository lotes;
    private final ModeloIARepository modelos;
    private final SegmentoRepository segmentos;
    private final TrazaIARepository trazas;
    private final JsonMapper json;
    private final Clock reloj;
    private final int kMinimo;
    private final int kMaximo;
    private final int tamanoMinimo;
    private final double tamanoMinimoPorcentaje;
    private final int reinicios;
    private final long semilla;
    private final int ventanaAnios;
    private final int muestraSilueta;

    public SegmentacionModeloService(JdbcTemplate jdbc, LoteETLRepository lotes, ModeloIARepository modelos,
            SegmentoRepository segmentos, TrazaIARepository trazas, JsonMapper json, Clock reloj,
            @Value("${tributario.segmentacion.k-minimo:2}") int kMinimo,
            @Value("${tributario.segmentacion.k-maximo:8}") int kMaximo,
            @Value("${tributario.segmentacion.tamano-minimo:3}") int tamanoMinimo,
            @Value("${tributario.segmentacion.tamano-minimo-porcentaje:2}") double tamanoMinimoPorcentaje,
            @Value("${tributario.segmentacion.reinicios:20}") int reinicios,
            @Value("${tributario.segmentacion.semilla:20261111}") long semilla,
            @Value("${tributario.segmentacion.ventana-anios:3}") int ventanaAnios,
            @Value("${tributario.segmentacion.muestra-silueta:5000}") int muestraSilueta) {
        this.jdbc = jdbc;
        this.lotes = lotes;
        this.modelos = modelos;
        this.segmentos = segmentos;
        this.trazas = trazas;
        this.json = json;
        this.reloj = reloj;
        this.kMinimo = Math.max(2, kMinimo);
        this.kMaximo = kMaximo;
        this.tamanoMinimo = Math.max(1, tamanoMinimo);
        this.tamanoMinimoPorcentaje = tamanoMinimoPorcentaje;
        this.reinicios = Math.max(1, reinicios);
        this.semilla = semilla;
        this.ventanaAnios = ventanaAnios;
        this.muestraSilueta = muestraSilueta;
    }

    record Contribuyente(String seudonimo, String segmentoBinario, double[] variables, double predios,
            double pctPredial, double pctEnLinea) {
    }

    @Transactional
    public CalculoSegmentacionRespuesta calcular(String solicitante) {
        LoteETL lote = lotes.buscarPublicadosConHechos(FUENTE_COBRANZA, Pageable.ofSize(1)).stream().findFirst()
                .orElseThrow(() -> new ReglaNegocioException("SIN_CARGA_ETL",
                        "No hay una carga del ETL publicada con hechos de cobranza para segmentar."));
        LocalDate corte = lote.getFechaCorte();
        List<Contribuyente> datos = variables(lote.getIdLote(), corte);
        int n = datos.size();
        int minimo = Math.max(tamanoMinimo, (int) Math.ceil(n * tamanoMinimoPorcentaje / 100.0));
        if (n < 2 * minimo) {
            throw new ReglaNegocioException("DATOS_INSUFICIENTES", "La carga " + lote.getIdLote() + " tiene " + n
                    + " contribuyentes con cuotas vencidas; se necesitan al menos " + 2 * minimo + ".");
        }

        double[][] crudo = datos.stream().map(Contribuyente::variables).toArray(double[][]::new);
        double[][] transformado = Arrays.stream(crudo).map(SegmentacionModeloService::transformar)
                .toArray(double[][]::new);
        double[] media = new double[VARIABLES.size()];
        double[] desviacion = new double[VARIABLES.size()];
        double[][] x = estandarizar(transformado, media, desviacion);

        List<CalculoSegmentacionRespuesta.EvaluacionK> evaluacion = new ArrayList<>();
        KMeans.Resultado elegido = null;
        double siluetaElegida = -2;
        for (int k = kMinimo; k <= Math.min(kMaximo, n / minimo); k++) {
            KMeans.Resultado r = KMeans.ajustar(x, k, reinicios, semilla);
            double s = KMeans.silueta(x, r.etiquetas(), k, muestraSilueta, semilla);
            boolean valido = r.tamanoMinimo() >= minimo;
            evaluacion.add(new CalculoSegmentacionRespuesta.EvaluacionK(k, redondear(s, 4), redondear(r.inercia(), 3),
                    r.tamanoMinimo(), valido));
            if (valido && s > siluetaElegida + 1e-9) {
                elegido = r;
                siluetaElegida = s;
            }
        }
        if (elegido == null) {
            throw new ReglaNegocioException("SEGMENTACION_SIN_K_VALIDO", "Ningún k entre " + kMinimo + " y "
                    + kMaximo + " deja todos los segmentos con al menos " + minimo + " contribuyentes.");
        }
        int k = elegido.k();
        int[] orden = ordenarDeMejorAPeorPagador(elegido, crudo);
        int[] etiquetas = new int[n];
        double[][] centros = new double[k][];
        for (int nuevo = 0; nuevo < k; nuevo++) {
            centros[nuevo] = elegido.centros()[orden[nuevo]];
        }
        int[] posicion = new int[k];
        for (int nuevo = 0; nuevo < k; nuevo++) {
            posicion[orden[nuevo]] = nuevo;
        }
        for (int i = 0; i < n; i++) {
            etiquetas[i] = posicion[elegido.etiquetas()[i]];
        }

        Map<String, Object> artefacto = new LinkedHashMap<>();
        artefacto.put("algoritmo", "k-means++ / Lloyd");
        artefacto.put("idLote", lote.getIdLote());
        artefacto.put("fechaCorte", corte.toString());
        artefacto.put("variables", VARIABLES);
        artefacto.put("transformacion", Map.of("diasAtraso", "signo(x)·ln(1+|x|)", "deudaVencidaPct", "ln(1+x)",
                "antiguedadDeudaAnios", "ln(1+x)", "mesesSinPago", "ln(1+x)", "pctATiempo", "x"));
        artefacto.put("media", redondear(media));
        artefacto.put("desviacion", redondear(desviacion));
        artefacto.put("k", k);
        artefacto.put("centros", Arrays.stream(centros).map(SegmentacionModeloService::redondear).toList());
        // Plano del mapa: las dos primeras componentes principales de las variables estandarizadas.
        Pca pca = componentesPrincipales(x, 2);
        artefacto.put("mapa", Map.of("componentes", Arrays.stream(pca.componentes())
                .map(SegmentacionModeloService::redondear).toList(), "varianzaExplicada", redondear(pca.varianza())));
        artefacto.put("parametros", Map.of("kMinimo", kMinimo, "kMaximo", kMaximo, "tamanoMinimo", minimo,
                "reinicios", reinicios, "semilla", semilla, "ventanaAnios", ventanaAnios));
        String artefactoJson = json.writeValueAsString(artefacto);
        String hash = sha256(artefactoJson);
        String version = "L" + lote.getIdLote() + "-" + hash.substring(0, 10);

        var existente = modelos.findByNombreAndVersion(NOMBRE_MODELO, version);
        if (existente.isPresent()) {
            List<SegmentoRespuesta> previos = segmentos.buscarPorModelo(existente.get().getIdModelo()).stream()
                    .map(SegmentoRespuesta::desde).toList();
            return new CalculoSegmentacionRespuesta(existente.get().getIdModelo(), version, lote.getIdLote(), corte,
                    n, k, redondear(siluetaElegida, 4), evaluacion, previos, false);
        }

        int descartados = jdbc.update("""
                DELETE FROM analitica.segmento s USING analitica.modelo_ia m
                WHERE m.id_modelo = s.id_modelo AND m.nombre = ? AND s.estado = 'BORRADOR'
                """, NOMBRE_MODELO);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        ModeloIA modelo = modelos.saveAndFlush(new ModeloIA(NOMBRE_MODELO, TipoModeloIA.SEGMENTACION, version, hash,
                "JSON", ahora));

        double[] siluetas = KMeans.siluetas(x, etiquetas, k, KMeans.muestra(n, muestraSilueta, semilla));
        boolean siluetaCompleta = n <= muestraSilueta;
        List<Segmento> nuevos = new ArrayList<>();
        for (int s = 0; s < k; s++) {
            List<Integer> miembros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (etiquetas[i] == s) {
                    miembros.add(i);
                }
            }
            Map<String, Object> caracteristicas = caracteristicas(datos, miembros, centros[s],
                    siluetaCompleta ? promedio(siluetas, miembros) : null);
            nuevos.add(new Segmento(modelo, nombre(s, centros[s]), descripcion(datos, miembros, n),
                    miembros.size(), json.writeValueAsString(caracteristicas)));
        }
        segmentos.saveAllAndFlush(nuevos);

        List<Object[]> filas = new ArrayList<>(n);
        Timestamp fecha = Timestamp.valueOf(ahora);
        for (int i = 0; i < n; i++) {
            double[] v = datos.get(i).variables();
            Map<String, Double> valores = new LinkedHashMap<>();
            for (int j = 0; j < VARIABLES.size(); j++) {
                valores.put(VARIABLES.get(j), redondear(v[j], 2));
            }
            filas.add(new Object[] {nuevos.get(etiquetas[i]).getIdSegmento(), datos.get(i).seudonimo(), fecha,
                    datos.get(i).segmentoBinario(), redondear(proyectar(x[i], pca.componentes()[0]), 5),
                    redondear(proyectar(x[i], pca.componentes()[1]), 5), json.writeValueAsString(valores)});
        }
        jdbc.batchUpdate("""
                INSERT INTO analitica.asignacion_segmento (id_segmento, seudonimo_contribuyente, fecha_asignacion,
                    segmento_binario_actual, coord_x, coord_y, variables)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, filas);

        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("idLote", lote.getIdLote());
        entrada.put("fechaCorte", corte.toString());
        entrada.put("contribuyentes", n);
        entrada.put("solicitante", solicitante);
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("modelo", artefacto);
        salida.put("evaluacion", evaluacion);
        salida.put("tamanos", nuevos.stream().map(Segmento::getTamano).toList());
        salida.put("borradoresReemplazados", descartados);
        trazas.save(new TrazaIA(modelo, json.writeValueAsString(entrada), json.writeValueAsString(salida), null,
                null, "silueta=" + redondear(siluetaElegida, 4), null, ahora));

        LOG.info("Segmentación: lote {} ({} contribuyentes) → k = {}, silueta {}, tamaños {}; modelo {} {}",
                lote.getIdLote(), n, k, redondear(siluetaElegida, 3), Arrays.toString(elegido.tamanos()), modelo
                        .getIdModelo(), version);
        return new CalculoSegmentacionRespuesta(modelo.getIdModelo(), version, lote.getIdLote(), corte, n, k,
                redondear(siluetaElegida, 4), evaluacion,
                nuevos.stream().map(SegmentoRespuesta::desde).toList(), true);
    }

    List<Contribuyente> variables(Long idLote, LocalDate corte) {
        Date c = Date.valueOf(corte);
        return jdbc.query(SQL_VARIABLES, (rs, i) -> new Contribuyente(rs.getString("c"),
                rs.getString("segmento_binario_actual"),
                new double[] {rs.getDouble("pct_a_tiempo"), rs.getDouble("dias_atraso"), rs.getDouble("deuda_pct"),
                    rs.getDouble("antiguedad"), rs.getDouble("meses_sin_pago")},
                rs.getDouble("predios"), rs.getDouble("pct_predial"), rs.getDouble("pct_en_linea")),
                c, c, c, ventanaAnios, idLote, c, idLote, c, idLote, c, c, 120);
    }

    /* Logaritmo en las variables de cola larga; el atraso conserva el signo (pagar adelantado es negativo). */
    static double[] transformar(double[] v) {
        return new double[] {v[0], Math.signum(v[1]) * Math.log1p(Math.abs(v[1])), Math.log1p(v[2]),
            Math.log1p(v[3]), Math.log1p(v[4])};
    }

    /* Media cero y desviación (poblacional) uno por columna; una columna constante queda en cero. */
    static double[][] estandarizar(double[][] v, double[] media, double[] desviacion) {
        int n = v.length;
        int d = v[0].length;
        for (int j = 0; j < d; j++) {
            double s = 0;
            for (double[] fila : v) {
                s += fila[j];
            }
            media[j] = s / n;
            double s2 = 0;
            for (double[] fila : v) {
                s2 += (fila[j] - media[j]) * (fila[j] - media[j]);
            }
            desviacion[j] = Math.sqrt(s2 / n);
            if (desviacion[j] == 0) {
                desviacion[j] = 1;
            }
        }
        double[][] x = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                x[i][j] = (v[i][j] - media[j]) / desviacion[j];
            }
        }
        return x;
    }

    record Pca(double[][] componentes, double[] varianza) {
    }

    /*
     * Componentes principales de x (columnas ya centradas): autovectores de la covarianza por el método de Jacobi,
     * ordenados por varianza. El signo se fija para que la carga de mayor valor absoluto sea positiva (determinista).
     */
    static Pca componentesPrincipales(double[][] x, int m) {
        int n = x.length;
        int d = x[0].length;
        double[][] a = new double[d][d];
        for (double[] fila : x) {
            for (int i = 0; i < d; i++) {
                for (int j = 0; j < d; j++) {
                    a[i][j] += fila[i] * fila[j] / n;
                }
            }
        }
        double[][] v = new double[d][d];
        for (int i = 0; i < d; i++) {
            v[i][i] = 1;
        }
        for (int barrido = 0; barrido < 100; barrido++) {
            double fuera = 0;
            for (int p = 0; p < d; p++) {
                for (int q = p + 1; q < d; q++) {
                    fuera += a[p][q] * a[p][q];
                }
            }
            if (fuera < 1e-22) {
                break;
            }
            for (int p = 0; p < d; p++) {
                for (int q = p + 1; q < d; q++) {
                    if (Math.abs(a[p][q]) < 1e-300) {
                        continue;
                    }
                    double theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    if (theta == 0) {
                        t = 1;
                    }
                    double c = 1 / Math.sqrt(t * t + 1);
                    double s = t * c;
                    for (int k = 0; k < d; k++) {
                        double akp = a[k][p];
                        double akq = a[k][q];
                        a[k][p] = c * akp - s * akq;
                        a[k][q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < d; k++) {
                        double apk = a[p][k];
                        double aqk = a[q][k];
                        a[p][k] = c * apk - s * aqk;
                        a[q][k] = s * apk + c * aqk;
                    }
                    for (int k = 0; k < d; k++) {
                        double vkp = v[k][p];
                        double vkq = v[k][q];
                        v[k][p] = c * vkp - s * vkq;
                        v[k][q] = s * vkp + c * vkq;
                    }
                }
            }
        }
        double total = 0;
        for (int i = 0; i < d; i++) {
            total += a[i][i];
        }
        Integer[] orden = new Integer[d];
        for (int i = 0; i < d; i++) {
            orden[i] = i;
        }
        double[][] diagonal = a;
        Arrays.sort(orden, (i, j) -> Double.compare(diagonal[j][j], diagonal[i][i]));
        double[][] componentes = new double[m][d];
        double[] varianza = new double[m];
        for (int c = 0; c < m; c++) {
            int col = orden[c];
            int mayor = 0;
            for (int k = 0; k < d; k++) {
                componentes[c][k] = v[k][col];
                if (Math.abs(v[k][col]) > Math.abs(v[mayor][col])) {
                    mayor = k;
                }
            }
            if (v[mayor][col] < 0) {
                for (int k = 0; k < d; k++) {
                    componentes[c][k] = -componentes[c][k];
                }
            }
            varianza[c] = total > 0 ? a[col][col] / total : 0;
        }
        return new Pca(componentes, varianza);
    }

    private static double proyectar(double[] punto, double[] componente) {
        double s = 0;
        for (int j = 0; j < punto.length; j++) {
            s += punto[j] * componente[j];
        }
        return s;
    }

    /* Numera los segmentos del de más cuotas a tiempo (en promedio) al de menos, para que el orden se lea solo. */
    private static int[] ordenarDeMejorAPeorPagador(KMeans.Resultado r, double[][] crudo) {
        double[] suma = new double[r.k()];
        int[] cuenta = r.tamanos();
        for (int i = 0; i < crudo.length; i++) {
            suma[r.etiquetas()[i]] += crudo[i][0];
        }
        Integer[] orden = new Integer[r.k()];
        for (int c = 0; c < r.k(); c++) {
            orden[c] = c;
        }
        Arrays.sort(orden, (a, b) -> Double.compare(suma[b] / cuenta[b], suma[a] / cuenta[a]));
        return Arrays.stream(orden).mapToInt(Integer::intValue).toArray();
    }

    /* "S3 · paga fuera de plazo, deuda antigua": las dos variables cuyo centro más se aleja de la media. */
    private static String nombre(int s, double[] centro) {
        Integer[] idx = new Integer[centro.length];
        for (int j = 0; j < centro.length; j++) {
            idx[j] = j;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(centro[b]), Math.abs(centro[a])));
        List<String> frases = new ArrayList<>();
        List<String> conceptos = new ArrayList<>();
        for (int j : idx) {
            String variable = VARIABLES.get(j);
            if (frases.size() == 2 || conceptos.contains(CONCEPTO.get(variable))) {
                continue;
            }
            frases.add(FRASES.get(variable)[centro[j] >= 0 ? 0 : 1]);
            conceptos.add(CONCEPTO.get(variable));
        }
        return "S" + (s + 1) + " · " + String.join(", ", frases);
    }

    private static String descripcion(List<Contribuyente> datos, List<Integer> miembros, int total) {
        double[] m = new double[VARIABLES.size()];
        for (int j = 0; j < m.length; j++) {
            m[j] = mediana(datos, miembros, j);
        }
        return String.format(Locale.ROOT, "%d contribuyentes (%d %%). Medianas: %.0f %% de cuotas a tiempo; atraso"
                + " medio de %.0f días (negativo: paga antes del vencimiento); deuda vencida %.0f %% de lo emitido;"
                + " deuda más antigua de %.1f años; último pago hace %.1f meses.", miembros.size(),
                Math.round(100.0 * miembros.size() / total), m[0], m[1], m[2], m[3], m[4]);
    }

    private static Map<String, Object> caracteristicas(List<Contribuyente> datos, List<Integer> miembros,
            double[] centro, Double silueta) {
        Map<String, Object> variables = new LinkedHashMap<>();
        for (int j = 0; j < VARIABLES.size(); j++) {
            variables.put(VARIABLES.get(j), Map.of("mediana", redondear(mediana(datos, miembros, j), 2),
                    "unidad", UNIDADES.get(VARIABLES.get(j)), "centroEstandarizado", redondear(centro[j], 3)));
        }
        Map<String, Object> descriptivas = new LinkedHashMap<>();
        descriptivas.put("prediosMediana", redondear(mediana(miembros, i -> datos.get(i).predios()), 1));
        descriptivas.put("pctImpuestoPredialMediana", redondear(mediana(miembros, i -> datos.get(i).pctPredial()), 1));
        descriptivas.put("pctPagosEnLineaMediana", redondear(mediana(miembros, i -> datos.get(i).pctEnLinea()), 1));
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("variables", variables);
        resultado.put("descriptivas", descriptivas);
        if (silueta != null) {
            resultado.put("siluetaMedia", redondear(silueta, 3));
        }
        return resultado;
    }

    private static double mediana(List<Contribuyente> datos, List<Integer> miembros, int j) {
        return mediana(miembros, i -> datos.get(i).variables()[j]);
    }

    private static double mediana(List<Integer> miembros, java.util.function.IntToDoubleFunction valor) {
        double[] v = miembros.stream().mapToDouble(valor::applyAsDouble).sorted().toArray();
        int m = v.length;
        return m % 2 == 1 ? v[m / 2] : (v[m / 2 - 1] + v[m / 2]) / 2;
    }

    private static double promedio(double[] valores, List<Integer> miembros) {
        return miembros.stream().mapToDouble(i -> valores[i]).average().orElse(0);
    }

    private static double redondear(double v, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(v * f) / f;
    }

    private static List<Double> redondear(double[] v) {
        return Arrays.stream(v).map(x -> redondear(x, 6)).boxed().toList();
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
