package pe.municipalidad.tributario.reporteria;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.dashboard.DashboardRepository;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Copia en memoria de las cuotas exigibles de la última carga publicada del ETL y su agregación por filtros (RF-012).
 * Usa las mismas reglas que el dashboard: lo exigible vence antes de la fecha de corte, lo cobrado es lo pagado de
 * esas cuotas y la deuda es su insoluto. Solo trabaja con seudónimos (RNF-018). Se vuelve a cargar cuando cambia la
 * carga publicada, los segmentos publicados o el umbral de PRICOS. Los importes se suman en céntimos.
 */
@Component
public class CuboReporteria {

    public static final String SIN_ZONA = "SIN_ZONA";
    public static final String SIN_SEGMENTO = "Sin segmento";

    static final List<String> CANALES = List.of("VENTANILLA", "EN_LINEA");
    static final int TRAMOS_ANTIGUEDAD = 4;
    static final int TRAMOS_ANTICIPACION = 4;

    private static final Logger LOG = LoggerFactory.getLogger(CuboReporteria.class);
    private static final BigDecimal UMBRAL_PRICO_POR_DEFECTO = BigDecimal.valueOf(2000);
    private static final String SIN_USO = "Sin uso registrado";
    private static final List<Tributo> TRIBUTOS = List.of(Tributo.values());

    public record Filtro(YearMonth desde, YearMonth hasta, String zona, String tributo, String segmento, String uso,
            Integer contribuyente) {

        Filtro conPeriodo(YearMonth nuevoDesde, YearMonth nuevoHasta) {
            return new Filtro(nuevoDesde, nuevoHasta, zona, tributo, segmento, uso, contribuyente);
        }
    }

    /* Catálogo y cuotas de una carga. Las columnas q* tienen una posición por cuota; las c*, por contribuyente. */
    record Carga(String version, long idLote, LocalDate fechaCorte, YearMonth primerMes, BigDecimal umbralPrico,
            List<String> zonas, List<String> segmentos, List<String> usos, String[] seudonimos, boolean[] juridica,
            int[] cz, int[] cs, int[] cu, boolean[] cp, int[] cpred, int nq, int[] qc, int[] qt, int[] qz, int[] qv,
            int[] qmp, boolean[] qpag, boolean[] qok, int[] qk, int[] qa, long[] qe, long[] qp, long[] qd, long[] qs) {

        int n() {
            return seudonimos.length;
        }

        String nombre(int c) {
            return "Contrib. " + seudonimos[c].substring(0, Math.min(8, seudonimos[c].length()));
        }

        String codigo(int c) {
            return seudonimos[c].substring(0, Math.min(6, seudonimos[c].length())).toUpperCase();
        }
    }

    /* Montos y conteos de cada valor de una dimensión. */
    static final class Grupo {
        final long[] emitido;
        final long[] cobrado;
        final long[] recaudado;
        final long[] deuda;
        final int[] cuotas;
        final int[] cuotasATiempo;
        final int[] contribuyentes;
        final int[] morosos;
        final int[] buenosPagadores;

        Grupo(int n) {
            emitido = new long[n];
            cobrado = new long[n];
            recaudado = new long[n];
            deuda = new long[n];
            cuotas = new int[n];
            cuotasATiempo = new int[n];
            contribuyentes = new int[n];
            morosos = new int[n];
            buenosPagadores = new int[n];
        }
    }

    /* Resultado de recorrer una vez las cuotas con los filtros aplicados. */
    static final class Agregado {
        Carga carga;
        Filtro filtro;
        int meses;
        long emitido;
        long cobrado;
        long recaudado;
        long deuda;
        int cuotas;
        int cuotasATiempo;
        int contribuyentes;
        int morosos;
        int buenosPagadores;
        long recaudadoBuenosPagadores;
        int pricos;
        int pricosMorosos;
        long pricosEmitido;
        long pricosCobrado;
        long pricosDeuda;
        Grupo porMes;
        long[] mesEmitidoPricos;
        long[] mesCobradoPricos;
        long[] mesEmitidoResto;
        long[] mesCobradoResto;
        long[][] saldoPorSegmento;
        Grupo porZona;
        Grupo porTributo;
        Grupo porSegmento;
        long[] tributoEmitidoPricos;
        long[][] zonaTributoEmitido;
        long[][] zonaTributoDeuda;
        long[] antiguedad;
        int[] canal;
        int[] anticipacion;
        long[] usoEmitido;
        long[] cEmitido;
        long[] cCobrado;
        long[] cRecaudado;
        long[] cDeuda;
        long[] cDeudaTributo;
        int[] cCuotas;
        int[] cATiempo;
        int[] cVencidas;
        int[] cAtraso;
        int[] cUltimoPago;
    }

    private final JdbcTemplate jdbc;
    private final DashboardRepository cargas;
    private volatile Carga vigente;

    public CuboReporteria(JdbcTemplate jdbc, DashboardRepository cargas) {
        this.jdbc = jdbc;
        this.cargas = cargas;
    }

    Carga carga() {
        LoteETL lote = cargas.buscarCargasPublicadasConHechos(PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new ReglaNegocioException("REPORTERIA_SIN_CARGA",
                        "No hay una carga publicada del ETL con hechos: no se puede mostrar la plataforma analítica."));
        BigDecimal umbral = umbralPrico();
        String version = lote.getIdLote() + "|" + lote.getFechaFin() + "|" + umbral + "|" + firmaSegmentos();
        Carga actual = vigente;
        if (actual != null && actual.version().equals(version)) {
            return actual;
        }
        synchronized (this) {
            if (vigente == null || !vigente.version().equals(version)) {
                long inicio = System.nanoTime();
                vigente = cargar(version, lote, umbral);
                LOG.info("Plataforma analítica: carga {} con {} contribuyentes y {} cuotas en {} ms", lote.getIdLote(),
                        vigente.n(), vigente.nq(), (System.nanoTime() - inicio) / 1_000_000);
            }
            return vigente;
        }
    }

    private String firmaSegmentos() {
        return jdbc.queryForObject("""
                SELECT count(*) || '/' || coalesce(max(a.id_asignacion), 0) || '/' || coalesce(max(s.fecha_aprobacion)::text, '')
                FROM analitica.segmento s
                LEFT JOIN analitica.asignacion_segmento a ON a.id_segmento = s.id_segmento
                WHERE s.estado = 'PUBLICADO'
                """, String.class);
    }

    private BigDecimal umbralPrico() {
        List<String> valores = jdbc.queryForList(
                "SELECT valor FROM seguridad.parametro_configuracion WHERE clave = 'UMBRAL_PRICO'", String.class);
        try {
            return valores.isEmpty() ? UMBRAL_PRICO_POR_DEFECTO : new BigDecimal(valores.get(0).strip());
        } catch (NumberFormatException ex) {
            LOG.warn("El parámetro UMBRAL_PRICO no es numérico; se usa el umbral por defecto");
            return UMBRAL_PRICO_POR_DEFECTO;
        }
    }

    private record FilaCuota(int c, int t, String zona, int qv, int qmp, boolean pagada, boolean aTiempo, int canal,
            int anticipacion, long emitido, long pagado, long deuda, long saldo) {
    }

    private Carga cargar(String version, LoteETL lote, BigDecimal umbral) {
        long idLote = lote.getIdLote();
        LocalDate corte = lote.getFechaCorte();

        Map<String, Integer> posiciones = new HashMap<>();
        List<String> seudonimos = new ArrayList<>();
        List<Boolean> juridicas = new ArrayList<>();
        jdbc.query("""
                SELECT d.seudonimo_contribuyente, d.tipo_persona
                FROM analitica.dim_contribuyente d
                WHERE EXISTS (SELECT 1 FROM analitica.hecho_cobranza h
                              WHERE h.id_lote = ? AND h.seudonimo_contribuyente = d.seudonimo_contribuyente)
                ORDER BY d.seudonimo_contribuyente
                """, rs -> {
            posiciones.put(rs.getString(1), seudonimos.size());
            seudonimos.add(rs.getString(1));
            juridicas.add("JURIDICA".equals(rs.getString(2)));
        }, idLote);
        int n = seudonimos.size();

        List<String> segmentos = new ArrayList<>(jdbc.queryForList(
                "SELECT DISTINCT nombre FROM analitica.segmento WHERE estado = 'PUBLICADO' ORDER BY nombre",
                String.class));
        int[] cs = new int[n];
        Arrays.fill(cs, -1);
        jdbc.query("""
                SELECT DISTINCT ON (a.seudonimo_contribuyente) a.seudonimo_contribuyente, s.nombre
                FROM analitica.asignacion_segmento a
                JOIN analitica.segmento s ON s.id_segmento = a.id_segmento
                WHERE s.estado = 'PUBLICADO'
                ORDER BY a.seudonimo_contribuyente, a.fecha_asignacion DESC, s.id_segmento DESC
                """, rs -> {
            Integer c = posiciones.get(rs.getString(1));
            if (c != null) {
                cs[c] = segmentos.indexOf(rs.getString(2));
            }
        });
        if (Arrays.stream(cs).anyMatch(s -> s < 0)) {
            int sinSegmento = segmentos.size();
            segmentos.add(SIN_SEGMENTO);
            for (int c = 0; c < n; c++) {
                cs[c] = cs[c] < 0 ? sinSegmento : cs[c];
            }
        }

        List<FilaCuota> filas = new ArrayList<>();
        TreeSet<String> zonasOrdenadas = new TreeSet<>((a, b) -> SIN_ZONA.equals(a) ? (SIN_ZONA.equals(b) ? 0 : 1)
                : SIN_ZONA.equals(b) ? -1 : a.compareTo(b));
        long[] cEmitidoAnio = new long[n];
        Map<String, Long> emisionPorZona = new HashMap<>();
        Map<String, Long> emisionPorUso = new HashMap<>();
        List<Set<Long>> predios = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            predios.add(new HashSet<>());
        }
        jdbc.query("""
                SELECT h.seudonimo_contribuyente, h.tributo, p.zona, p.id_predio, p.uso_predio, p.tipo_predio,
                       h.anio, h.fecha_vencimiento, h.monto_emitido, h.monto_pagado, h.monto_insoluto,
                       h.monto_descuento, h.estado_pago, h.fecha_pago, h.canal_pago
                FROM analitica.hecho_cobranza h
                JOIN analitica.dim_predio p ON p.id_predio = h.id_predio
                WHERE h.id_lote = ?
                """, rs -> {
            Integer c = posiciones.get(rs.getString("seudonimo_contribuyente"));
            int t = posicionTributo(rs.getString("tributo"));
            if (c == null || t < 0) {
                return;
            }
            long emitido = centimos(rs, "monto_emitido");
            String zona = rs.getString("zona");
            String tipoPredio = rs.getString("tipo_predio");
            if (rs.getInt("anio") == corte.getYear()) {
                cEmitidoAnio[c] += emitido;
            }
            if (!"OTRO".equals(tipoPredio)) {
                predios.get(c).add(rs.getLong("id_predio"));
                String uso = rs.getString("uso_predio");
                emisionPorUso.merge(c + "|" + (uso == null || uso.isBlank() ? SIN_USO : uso), emitido, Long::sum);
            }
            LocalDate vence = rs.getObject("fecha_vencimiento", LocalDate.class);
            if (!vence.isBefore(corte)) {
                return;
            }
            emisionPorZona.merge(c + "|" + zona, emitido, Long::sum);
            zonasOrdenadas.add(zona);
            filas.add(filaCuota(rs, c, t, zona, vence, emitido));
        }, idLote);

        List<String> zonas = new ArrayList<>(zonasOrdenadas);
        int[] cz = principalPorContribuyente(n, emisionPorZona, zonas);
        boolean[] cp = new boolean[n];
        long umbralCentimos = umbral.movePointRight(2).longValue();
        for (int c = 0; c < n; c++) {
            cp[c] = cEmitidoAnio[c] >= umbralCentimos && cEmitidoAnio[c] > 0;
        }
        TreeSet<String> usosPricos = new TreeSet<>();
        emisionPorUso.keySet().forEach(clave -> {
            int separador = clave.indexOf('|');
            if (cp[Integer.parseInt(clave.substring(0, separador))]) {
                usosPricos.add(clave.substring(separador + 1));
            }
        });
        List<String> usos = new ArrayList<>(usosPricos);
        int[] cu = principalPorContribuyente(n, emisionPorUso, usos);
        int[] cpred = predios.stream().mapToInt(Set::size).toArray();

        int nq = filas.size();
        int[] qc = new int[nq];
        int[] qt = new int[nq];
        int[] qz = new int[nq];
        int[] qv = new int[nq];
        int[] qmp = new int[nq];
        int[] qk = new int[nq];
        int[] qa = new int[nq];
        boolean[] qpag = new boolean[nq];
        boolean[] qok = new boolean[nq];
        long[] qe = new long[nq];
        long[] qp = new long[nq];
        long[] qd = new long[nq];
        long[] qs = new long[nq];
        Map<String, Integer> indiceZona = new HashMap<>();
        for (int z = 0; z < zonas.size(); z++) {
            indiceZona.put(zonas.get(z), z);
        }
        int primerMes = mes(YearMonth.from(corte));
        for (int i = 0; i < nq; i++) {
            FilaCuota f = filas.get(i);
            qc[i] = f.c();
            qt[i] = f.t();
            qz[i] = indiceZona.get(f.zona());
            qv[i] = f.qv();
            qmp[i] = f.qmp();
            qk[i] = f.canal();
            qa[i] = f.anticipacion();
            qpag[i] = f.pagada();
            qok[i] = f.aTiempo();
            qe[i] = f.emitido();
            qp[i] = f.pagado();
            qd[i] = f.deuda();
            qs[i] = f.saldo();
            primerMes = Math.min(primerMes, f.qv());
        }
        return new Carga(version, idLote, corte, YearMonth.of(primerMes / 12, primerMes % 12 + 1), umbral, zonas,
                segmentos, usos, seudonimos.toArray(String[]::new), toBooleans(juridicas), cz, cs, cu, cp, cpred, nq,
                qc, qt, qz, qv, qmp, qpag, qok, qk, qa, qe, qp, qd, qs);
    }

    private static FilaCuota filaCuota(ResultSet rs, int c, int t, String zona, LocalDate vence, long emitido)
            throws SQLException {
        long pagado = centimos(rs, "monto_pagado");
        long insoluto = centimos(rs, "monto_insoluto");
        long descuento = centimos(rs, "monto_descuento");
        boolean pagada = "PAGADA".equals(rs.getString("estado_pago"));
        LocalDate fechaPago = rs.getObject("fecha_pago", LocalDate.class);
        int mesPago = fechaPago != null ? mes(YearMonth.from(fechaPago)) : pagado > 0 ? mes(YearMonth.from(vence)) : -1;
        boolean aTiempo = pagada && (fechaPago != null ? !fechaPago.isAfter(vence) : pagado == 0);
        int anticipacion = aTiempo && fechaPago != null ? tramoAnticipacion(ChronoUnit.DAYS.between(fechaPago, vence)) : -1;
        String canal = rs.getString("canal_pago");
        return new FilaCuota(c, t, zona, mes(YearMonth.from(vence)), mesPago, pagada, aTiempo,
                canal == null ? -1 : CANALES.indexOf(canal), anticipacion, emitido, pagado, pagada ? 0 : insoluto,
                pagada ? Math.max(emitido - descuento, 0) : insoluto);
    }

    private static int tramoAnticipacion(long dias) {
        return dias > 30 ? 0 : dias >= 15 ? 1 : dias >= 1 ? 2 : 3;
    }

    private static int posicionTributo(String codigo) {
        for (int t = 0; t < TRIBUTOS.size(); t++) {
            if (TRIBUTOS.get(t).name().equals(codigo)) {
                return t;
            }
        }
        return -1;
    }

    private static long centimos(ResultSet rs, String columna) throws SQLException {
        return rs.getBigDecimal(columna).movePointRight(2).longValue();
    }

    private static boolean[] toBooleans(List<Boolean> valores) {
        boolean[] resultado = new boolean[valores.size()];
        for (int i = 0; i < resultado.length; i++) {
            resultado[i] = valores.get(i);
        }
        return resultado;
    }

    /* Para cada contribuyente, el valor con más emisión; las claves del mapa son "contribuyente|valor". */
    private static int[] principalPorContribuyente(int n, Map<String, Long> emision, List<String> valores) {
        int[] principal = new int[n];
        long[] mayor = new long[n];
        Arrays.fill(principal, -1);
        Arrays.fill(mayor, -1);
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < valores.size(); i++) {
            indice.put(valores.get(i), i);
        }
        emision.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entrada -> {
            int separador = entrada.getKey().indexOf('|');
            int c = Integer.parseInt(entrada.getKey().substring(0, separador));
            Integer valor = indice.get(entrada.getKey().substring(separador + 1));
            if (valor != null && entrada.getValue() > mayor[c]) {
                mayor[c] = entrada.getValue();
                principal[c] = valor;
            }
        });
        return principal;
    }

    static int mes(YearMonth mes) {
        return mes.getYear() * 12 + mes.getMonthValue() - 1;
    }

    static List<Tributo> tributos() {
        return TRIBUTOS;
    }

    Agregado calcular(Filtro filtro) {
        return calcular(carga(), filtro);
    }

    Agregado calcular(Carga d, Filtro f) {
        int n = d.n();
        int nt = TRIBUTOS.size();
        int nz = d.zonas().size();
        int ns = d.segmentos().size();
        int desde = mes(f.desde());
        int hasta = mes(f.hasta());
        int m = hasta - desde + 1;
        int corte = mes(YearMonth.from(d.fechaCorte()));
        int zonaFiltro = indiceFiltro(d.zonas(), f.zona());
        int segmentoFiltro = indiceFiltro(d.segmentos(), f.segmento());
        int usoFiltro = indiceFiltro(d.usos(), f.uso());
        int tributoFiltro = f.tributo() == null ? -1 : Optional.of(posicionTributo(f.tributo())).filter(t -> t >= 0).orElse(-2);
        int contribuyenteFiltro = f.contribuyente() == null ? -1 : f.contribuyente();

        Agregado a = new Agregado();
        a.carga = d;
        a.filtro = f;
        a.meses = m;
        a.porMes = new Grupo(m);
        a.mesEmitidoPricos = new long[m];
        a.mesCobradoPricos = new long[m];
        a.mesEmitidoResto = new long[m];
        a.mesCobradoResto = new long[m];
        a.saldoPorSegmento = new long[ns][m];
        a.porZona = new Grupo(nz);
        a.porTributo = new Grupo(nt);
        a.porSegmento = new Grupo(ns);
        a.tributoEmitidoPricos = new long[nt];
        a.zonaTributoEmitido = new long[nz][nt];
        a.zonaTributoDeuda = new long[nz][nt];
        a.antiguedad = new long[TRAMOS_ANTIGUEDAD];
        a.canal = new int[CANALES.size()];
        a.anticipacion = new int[TRAMOS_ANTICIPACION];
        a.usoEmitido = new long[d.usos().size()];
        a.cEmitido = new long[n];
        a.cCobrado = new long[n];
        a.cRecaudado = new long[n];
        a.cDeuda = new long[n];
        a.cDeudaTributo = new long[n * nt];
        a.cCuotas = new int[n];
        a.cATiempo = new int[n];
        a.cVencidas = new int[n];
        a.cAtraso = new int[n];
        a.cUltimoPago = new int[n];
        Arrays.fill(a.cAtraso, -1);
        Arrays.fill(a.cUltimoPago, -1);
        int[] zonaCuotas = new int[n * nz];
        int[] zonaATiempo = new int[n * nz];
        int[] zonaVencidas = new int[n * nz];
        int[] tributoCuotas = new int[n * nt];
        int[] tributoATiempo = new int[n * nt];
        int[] tributoVencidas = new int[n * nt];

        for (int i = 0; i < d.nq(); i++) {
            int c = d.qc()[i];
            int z = d.qz()[i];
            int t = d.qt()[i];
            int s = d.cs()[c];
            if ((zonaFiltro != -1 && z != zonaFiltro) || (segmentoFiltro != -1 && s != segmentoFiltro)
                    || (tributoFiltro != -1 && t != tributoFiltro)
                    || (contribuyenteFiltro != -1 && c != contribuyenteFiltro)
                    || (usoFiltro != -1 && (!d.cp()[c] || d.cu()[c] != usoFiltro))) {
                continue;
            }
            int vence = d.qv()[i];
            int mesPago = d.qmp()[i];
            long emitido = d.qe()[i];
            long pagado = d.qp()[i];
            long deuda = d.qd()[i];
            boolean pagada = d.qpag()[i];
            boolean aTiempo = d.qok()[i];
            boolean prico = d.cp()[c];
            if (mesPago >= 0 && mesPago >= desde && mesPago <= hasta && pagado > 0) {
                a.recaudado += pagado;
                a.porMes.recaudado[mesPago - desde] += pagado;
                a.porZona.recaudado[z] += pagado;
                a.porTributo.recaudado[t] += pagado;
                a.porSegmento.recaudado[s] += pagado;
                a.cRecaudado[c] += pagado;
            }
            if (mesPago > a.cUltimoPago[c] && pagado > 0) {
                a.cUltimoPago[c] = mesPago;
            }
            if (vence < desde || vence > hasta) {
                continue;
            }
            int j = vence - desde;
            a.emitido += emitido;
            a.cobrado += pagado;
            a.deuda += deuda;
            a.cuotas++;
            sumar(a.porMes, j, emitido, pagado, deuda, aTiempo);
            sumar(a.porZona, z, emitido, pagado, deuda, aTiempo);
            sumar(a.porTributo, t, emitido, pagado, deuda, aTiempo);
            sumar(a.porSegmento, s, emitido, pagado, deuda, aTiempo);
            a.zonaTributoEmitido[z][t] += emitido;
            a.zonaTributoDeuda[z][t] += deuda;
            a.cEmitido[c] += emitido;
            a.cCobrado[c] += pagado;
            a.cDeuda[c] += deuda;
            a.cDeudaTributo[c * nt + t] += deuda;
            a.cCuotas[c]++;
            zonaCuotas[c * nz + z]++;
            tributoCuotas[c * nt + t]++;
            if (prico) {
                a.mesEmitidoPricos[j] += emitido;
                a.mesCobradoPricos[j] += pagado;
                a.tributoEmitidoPricos[t] += emitido;
                if (d.cu()[c] >= 0) {
                    a.usoEmitido[d.cu()[c]] += emitido;
                }
            } else {
                a.mesEmitidoResto[j] += emitido;
                a.mesCobradoResto[j] += pagado;
            }
            if (aTiempo) {
                a.cuotasATiempo++;
                a.cATiempo[c]++;
                zonaATiempo[c * nz + z]++;
                tributoATiempo[c * nt + t]++;
                if (d.qk()[i] >= 0) {
                    a.canal[d.qk()[i]]++;
                }
                if (d.qa()[i] >= 0) {
                    a.anticipacion[d.qa()[i]]++;
                }
            }
            if (!pagada && deuda > 0) {
                a.cVencidas[c]++;
                zonaVencidas[c * nz + z]++;
                tributoVencidas[c * nt + t]++;
                int atraso = corte - vence;
                a.cAtraso[c] = Math.max(a.cAtraso[c], atraso);
                a.antiguedad[atraso <= 3 ? 0 : atraso <= 6 ? 1 : atraso <= 12 ? 2 : 3] += deuda;
            }
            int finSaldo = pagada && mesPago >= 0 ? Math.min(mesPago - 1, hasta) : hasta;
            long saldo = d.qs()[i];
            for (int k = vence; k <= finSaldo; k++) {
                a.saldoPorSegmento[s][k - desde] += saldo;
            }
        }

        for (int c = 0; c < n; c++) {
            if (a.cCuotas[c] == 0) {
                continue;
            }
            int s = d.cs()[c];
            boolean moroso = a.cDeuda[c] > 0;
            boolean bueno = a.cATiempo[c] == a.cCuotas[c];
            a.contribuyentes++;
            a.porSegmento.contribuyentes[s]++;
            if (moroso) {
                a.morosos++;
                a.porSegmento.morosos[s]++;
            }
            if (bueno) {
                a.buenosPagadores++;
                a.porSegmento.buenosPagadores[s]++;
                a.recaudadoBuenosPagadores += a.cRecaudado[c];
            }
            if (d.cp()[c]) {
                a.pricos++;
                a.pricosEmitido += a.cEmitido[c];
                a.pricosCobrado += a.cCobrado[c];
                a.pricosDeuda += a.cDeuda[c];
                if (moroso) {
                    a.pricosMorosos++;
                }
            }
            contarPorContribuyente(a.porZona, c, nz, zonaCuotas, zonaATiempo, zonaVencidas);
            contarPorContribuyente(a.porTributo, c, nt, tributoCuotas, tributoATiempo, tributoVencidas);
        }
        return a;
    }

    private static int indiceFiltro(List<String> valores, String valor) {
        if (valor == null) {
            return -1;
        }
        int indice = valores.indexOf(valor);
        return indice < 0 ? -2 : indice;
    }

    private static void sumar(Grupo g, int i, long emitido, long pagado, long deuda, boolean aTiempo) {
        g.emitido[i] += emitido;
        g.cobrado[i] += pagado;
        g.deuda[i] += deuda;
        g.cuotas[i]++;
        if (aTiempo) {
            g.cuotasATiempo[i]++;
        }
    }

    private static void contarPorContribuyente(Grupo g, int c, int valores, int[] cuotas, int[] aTiempo, int[] vencidas) {
        for (int v = 0; v < valores; v++) {
            int cuotasValor = cuotas[c * valores + v];
            if (cuotasValor == 0) {
                continue;
            }
            g.contribuyentes[v]++;
            if (vencidas[c * valores + v] > 0) {
                g.morosos[v]++;
            }
            if (aTiempo[c * valores + v] == cuotasValor) {
                g.buenosPagadores[v]++;
            }
        }
    }
}
