package pe.municipalidad.tributario.reporteria;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.reporteria.ConsultaAsistente.Agrupacion;
import pe.municipalidad.tributario.reporteria.ConsultaAsistente.Indicador;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Agregado;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Carga;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Filtro;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Grupo;
import pe.municipalidad.tributario.reporteria.ModeloLenguajeCliente.Completado;
import pe.municipalidad.tributario.reporteria.ModeloLenguajeCliente.Mensaje;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * Asistente IA de la plataforma analítica (RF-058): pregunta, consulta validada, agregados y respuesta en español.
 * El modelo solo produce texto. Propone una consulta que se valida contra el catálogo y se resuelve con los agregados
 * de las pantallas; sus respuestas pasan por la comprobación de cifras y, si inventa, se muestra el resultado.
 */
@Service
public class AsistenteReporteriaService {

    private static final Pattern BLOQUE_JSON = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOQUE_GRAFICO = Pattern.compile("```(?:grafico|json)\\s*(.*?)```", Pattern.DOTALL);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final int TOKENS_CONSULTA = 400;
    private static final int TOKENS_RESPUESTA = 700;
    private static final int LIMITE_CONTRIBUYENTES = 10;
    private static final String SISTEMA_RESPUESTA = """
            Eres el asistente de datos de la Gerencia de Rentas de la Municipalidad de San Miguel.
            Respondes en español, de forma clara y breve, usando solo las cifras del resultado que te dan.""";
    private static final String FORMATO = """
            Responde a la Gerencia de Rentas. Primero la respuesta directa en una o dos frases, con montos en soles (S/) \
            y porcentajes con un decimal; si comparas porcentajes, la diferencia va en puntos porcentuales (pp). \
            Después, si ayuda, agrega un elemento visual pequeño: una tabla en Markdown, o un gráfico en un bloque \
            ```grafico con JSON así: {"tipo": "barras" | "lineas" | "torta", "titulo": "...", \
            "categorias": [...], "series": [{"nombre": "...", "valores": [...]}]}. \
            Usa solo las cifras del resultado; no inventes datos.""";
    private static final String SIN_CONSULTA = "No pude armar una consulta válida para esa pregunta. Prueba a formularla "
            + "de otra manera (por ejemplo, indicando el año o la zona).";
    private static final String SIN_DATOS = "La consulta no devolvió datos para esa pregunta. Prueba con otro periodo, zona o tributo.";

    private final ModeloLenguajeCliente modelo;
    private final CuboReporteria cubo;
    private final ReporteriaService reporteria;
    private final JsonMapper json;
    private final String guia;

    public AsistenteReporteriaService(ModeloLenguajeCliente modelo, CuboReporteria cubo, ReporteriaService reporteria,
            JsonMapper json) {
        this.modelo = modelo;
        this.cubo = cubo;
        this.reporteria = reporteria;
        this.json = json;
        this.guia = leerGuia();
    }

    private static String leerGuia() {
        try (InputStream entrada = new ClassPathResource("reporteria/guia_asistente.md").getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer la guía del asistente", ex);
        }
    }

    public ModeloLenguajeCliente.Salud estado() {
        return modelo.salud();
    }

    public RespuestaAsistente preguntar(PreguntaAsistentePeticion peticion) {
        ReporteriaService.validarPeriodo(peticion.desde(), peticion.hasta());
        long inicio = System.nanoTime();
        CatalogoReporteriaRespuesta catalogo = reporteria.catalogo();
        Carga carga = cubo.carga();
        String contexto = contexto(peticion);

        List<Mensaje> conversacion = new ArrayList<>(List.of(
                new Mensaje("system", guia + "\n" + catalogoComoTexto(catalogo, carga)),
                new Mensaje("user", "Pregunta: " + peticion.pregunta() + "\nFiltros de la pantalla: " + contexto
                        + "\nEscribe solo el JSON en un bloque ```json.")));
        ConsultaAsistente consulta = null;
        String nombreModelo = "";
        boolean corregida = false;
        for (int intento = 0; intento < 2 && consulta == null; intento++) {
            Completado completado = modelo.completar(conversacion, TOKENS_CONSULTA);
            nombreModelo = completado.modelo();
            try {
                consulta = ConsultaAsistente.de(leerJson(completado.texto()), catalogo);
                corregida = intento > 0;
            } catch (ConsultaAsistente.Invalida ex) {
                conversacion.add(new Mensaje("assistant", completado.texto()));
                conversacion.add(new Mensaje("user", "La consulta no es válida: " + ex.getMessage()
                        + "\nCorrígela. Escribe solo el JSON en un bloque ```json."));
            }
        }
        LocalDate corte = carga.fechaCorte();
        if (consulta == null) {
            return new RespuestaAsistente(SIN_CONSULTA, null, true, List.of(), List.of(), List.of(), corte, nombreModelo,
                    segundos(inicio), false);
        }
        Resultado resultado = ejecutar(consulta, peticion, carga);
        String descripcion = describir(consulta, peticion, carga);
        if (resultado.filas().isEmpty()) {
            return new RespuestaAsistente(SIN_DATOS, descripcion, corregida, resultado.columnas(), List.of(), List.of(),
                    corte, nombreModelo, segundos(inicio), false);
        }
        String fechaCorte = corte.format(FORMATO_FECHA);
        List<Mensaje> redaccion = new ArrayList<>(List.of(new Mensaje("system", SISTEMA_RESPUESTA),
                new Mensaje("user", "Pregunta: " + peticion.pregunta() + "\n\nConsulta: " + descripcion
                        + "\n\nResultado (CSV):\n" + csv(resultado) + "\n\nFecha de corte de los datos: " + fechaCorte
                        + ".\n\n" + FORMATO)));
        List<String> textos = new ArrayList<>(List.of(peticion.pregunta(), contexto, fechaCorte, descripcion));
        textos.addAll(resultado.columnas());
        String respuesta = null;
        for (int intento = 0; intento < 2 && respuesta == null; intento++) {
            String texto = modelo.completar(redaccion, TOKENS_RESPUESTA).texto();
            List<Double> inventadas = VerificadorCifras.inventadas(texto, resultado.filas(), textos);
            if (inventadas.isEmpty()) {
                respuesta = texto;
            } else {
                redaccion.add(new Mensaje("assistant", texto));
                redaccion.add(new Mensaje("user", "Tu respuesta tiene cifras que no están en el resultado: "
                        + inventadas.stream().limit(10).map(String::valueOf).collect(Collectors.joining(", "))
                        + ". Reescríbela usando solo las cifras del resultado. No agregues años, meses ni valores que no estén ahí."));
            }
        }
        boolean reemplazada = respuesta == null;
        if (reemplazada) {
            respuesta = "Este es el resultado de la consulta (la IA agregó cifras que no estaban en los datos, así que "
                    + "se muestra solo el resultado):\n\n" + tablaMarkdown(resultado);
        }
        return new RespuestaAsistente(respuesta, descripcion, corregida, resultado.columnas(), resultado.filas(),
                reemplazada ? List.of() : graficos(respuesta), corte, nombreModelo, segundos(inicio), reemplazada);
    }

    private static double segundos(long inicio) {
        return Math.round((System.nanoTime() - inicio) / 100_000_000.0) / 10.0;
    }

    private JsonNode leerJson(String texto) {
        Matcher bloque = BLOQUE_JSON.matcher(texto == null ? "" : texto);
        String contenido = bloque.find() ? bloque.group(1).strip() : texto == null ? "" : texto.strip();
        try {
            return json.readTree(contenido);
        } catch (JacksonException ex) {
            throw new ConsultaAsistente.Invalida("La respuesta no es un JSON válido.");
        }
    }

    private List<Map<String, Object>> graficos(String texto) {
        List<Map<String, Object>> graficos = new ArrayList<>();
        Matcher bloque = BLOQUE_GRAFICO.matcher(texto);
        while (bloque.find()) {
            try {
                Map<String, Object> grafico = json.readValue(bloque.group(1), new TypeReference<Map<String, Object>>() { });
                if (grafico.get("series") instanceof List<?> && grafico.get("categorias") instanceof List<?>) {
                    graficos.add(grafico);
                }
            } catch (JacksonException ex) {
                continue;
            }
        }
        return graficos;
    }

    private static String catalogoComoTexto(CatalogoReporteriaRespuesta c, Carga carga) {
        return "\nCatálogo vigente:\n- Zonas: " + c.zonas().stream().map(o -> o.codigo()).collect(Collectors.joining(", "))
                + "\n- Tributos: " + c.tributos().stream().map(o -> o.codigo() + " (" + o.nombre() + ")").collect(Collectors.joining(", "))
                + "\n- Segmentos: " + String.join(", ", c.segmentos())
                + "\n- Años con datos: desde " + carga.primerMes().getYear() + " hasta " + carga.fechaCorte().getYear()
                + "\n- El año de la fecha de corte (" + carga.fechaCorte().format(FORMATO_FECHA) + ") todavía está incompleto.\n";
    }

    private static String contexto(PreguntaAsistentePeticion p) {
        List<String> partes = new ArrayList<>();
        partes.add("periodo de " + p.desde() + " a " + p.hasta());
        if (p.zona() != null) {
            partes.add("zona " + p.zona());
        }
        if (p.tributo() != null) {
            partes.add("tributo " + p.tributo());
        }
        if (p.segmento() != null) {
            partes.add("segmento " + p.segmento());
        }
        return String.join("; ", partes);
    }

    private record Resultado(List<String> columnas, List<List<Object>> filas) {
    }

    /* Una consulta se resuelve con una pasada por el cubo; con varios años, una por año. */
    private Resultado ejecutar(ConsultaAsistente q, PreguntaAsistentePeticion p, Carga carga) {
        String zona = q.zona() != null ? q.zona() : p.zona();
        String tributo = q.tributo() != null ? q.tributo() : p.tributo();
        String segmento = q.segmento() != null ? q.segmento() : p.segmento();
        Filtro base = new Filtro(p.desde(), p.hasta(), zona, tributo, segmento, null, null);
        YearMonth ultimoMes = YearMonth.from(carga.fechaCorte());

        List<Integer> anios = q.anios();
        if (q.agrupacion() == Agrupacion.ANIO && anios.isEmpty()) {
            anios = IntStream.rangeClosed(carga.primerMes().getYear(), ultimoMes.getYear()).boxed().toList();
        }
        boolean porAnio = !anios.isEmpty();
        boolean columnaAnio = porAnio && (anios.size() > 1 || q.agrupacion() == Agrupacion.ANIO);
        List<String> columnas = new ArrayList<>();
        if (columnaAnio) {
            columnas.add("Año");
        }
        if (q.agrupacion() != Agrupacion.NINGUNO && q.agrupacion() != Agrupacion.ANIO) {
            columnas.add(etiquetaAgrupacion(q.agrupacion()));
        }
        q.indicadores().forEach(i -> columnas.add(i.etiqueta));

        List<List<Object>> filas = new ArrayList<>();
        List<Filtro> pasadas = porAnio
                ? anios.stream().map(anio -> base.conPeriodo(YearMonth.of(anio, 1),
                        YearMonth.of(anio, 12).isAfter(ultimoMes) ? ultimoMes : YearMonth.of(anio, 12))).toList()
                : List.of(base);
        for (Filtro filtro : pasadas) {
            Agregado a = cubo.calcular(carga, filtro);
            List<Object> prefijo = columnaAnio ? List.of(filtro.desde().getYear()) : List.of();
            filas.addAll(filasDe(a, q, prefijo));
        }
        int inicioIndicadores = columnas.size() - q.indicadores().size();
        if (q.orden() != null) {
            Comparator<List<Object>> comparador = Comparator.comparingDouble(
                    fila -> fila.get(inicioIndicadores) instanceof Number n ? n.doubleValue() : Double.NEGATIVE_INFINITY);
            filas.sort("desc".equals(q.orden()) ? comparador.reversed() : comparador);
        }
        int limite = q.limite() != null ? q.limite()
                : q.agrupacion() == Agrupacion.CONTRIBUYENTE ? LIMITE_CONTRIBUYENTES : ConsultaAsistente.LIMITE_MAXIMO;
        return new Resultado(columnas, filas.size() > limite ? new ArrayList<>(filas.subList(0, limite)) : filas);
    }

    private static String etiquetaAgrupacion(Agrupacion agrupacion) {
        return switch (agrupacion) {
            case MES -> "Mes";
            case ZONA -> "Zona";
            case TRIBUTO -> "Tributo";
            case SEGMENTO -> "Segmento";
            case CONTRIBUYENTE -> "Contribuyente";
            default -> "";
        };
    }

    private static List<List<Object>> filasDe(Agregado a, ConsultaAsistente q, List<Object> prefijo) {
        Carga d = a.carga;
        List<List<Object>> filas = new ArrayList<>();
        switch (q.agrupacion()) {
            case NINGUNO, ANIO -> {
                if (a.cuotas > 0 || a.recaudado > 0) {
                    filas.add(fila(prefijo, null, q, i -> total(a, i)));
                }
            }
            case MES -> {
                int desde = CuboReporteria.mes(a.filtro.desde());
                for (int m = 0; m < a.meses; m++) {
                    int indice = m;
                    int absoluto = desde + m;
                    if (a.porMes.cuotas[m] > 0 || a.porMes.recaudado[m] > 0) {
                        filas.add(fila(prefijo, MESES[absoluto % 12] + " " + absoluto / 12, q, i -> deGrupo(a.porMes, indice, i)));
                    }
                }
            }
            case ZONA -> agregarGrupo(filas, a.porZona, d.zonas(), prefijo, q);
            case TRIBUTO -> agregarGrupo(filas, a.porTributo, CuboReporteria.tributos().stream()
                    .map(t -> t.getDescripcion()).toList(), prefijo, q);
            case SEGMENTO -> agregarGrupo(filas, a.porSegmento, d.segmentos(), prefijo, q);
            case CONTRIBUYENTE -> {
                for (int c = 0; c < d.n(); c++) {
                    int indice = c;
                    if (a.cCuotas[c] > 0 || a.cRecaudado[c] > 0) {
                        filas.add(fila(prefijo, d.nombre(c), q, i -> deContribuyente(a, indice, i)));
                    }
                }
            }
            default -> throw new IllegalStateException("Agrupación desconocida");
        }
        return filas;
    }

    private static void agregarGrupo(List<List<Object>> filas, Grupo g, List<String> nombres, List<Object> prefijo,
            ConsultaAsistente q) {
        for (int v = 0; v < nombres.size(); v++) {
            int indice = v;
            if (g.cuotas[v] > 0 || g.recaudado[v] > 0) {
                filas.add(fila(prefijo, nombres.get(v), q, i -> deGrupo(g, indice, i)));
            }
        }
    }

    private static List<Object> fila(List<Object> prefijo, String etiqueta, ConsultaAsistente q,
            Function<Indicador, Object> valor) {
        List<Object> fila = new ArrayList<>(prefijo);
        if (etiqueta != null) {
            fila.add(etiqueta);
        }
        q.indicadores().forEach(i -> fila.add(valor.apply(i)));
        return fila;
    }

    private static Object total(Agregado a, Indicador i) {
        return valor(i, a.emitido, a.cobrado, a.recaudado, a.deuda, a.cuotas, a.cuotasATiempo, a.contribuyentes, a.morosos,
                a.buenosPagadores);
    }

    private static Object deGrupo(Grupo g, int v, Indicador i) {
        return valor(i, g.emitido[v], g.cobrado[v], g.recaudado[v], g.deuda[v], g.cuotas[v], g.cuotasATiempo[v],
                g.contribuyentes[v], g.morosos[v], g.buenosPagadores[v]);
    }

    private static Object deContribuyente(Agregado a, int c, Indicador i) {
        boolean activo = a.cCuotas[c] > 0;
        return valor(i, a.cEmitido[c], a.cCobrado[c], a.cRecaudado[c], a.cDeuda[c], a.cCuotas[c], a.cATiempo[c],
                activo ? 1 : 0, a.cDeuda[c] > 0 ? 1 : 0, activo && a.cATiempo[c] == a.cCuotas[c] ? 1 : 0);
    }

    private static Object valor(Indicador i, long emitido, long cobrado, long recaudado, long deuda, int cuotas,
            int aTiempo, int contribuyentes, int morosos, int buenos) {
        return switch (i) {
            case EMITIDO -> ReporteriaService.soles(emitido);
            case COBRADO -> ReporteriaService.soles(cobrado);
            case RECAUDADO -> ReporteriaService.soles(recaudado);
            case DEUDA_VENCIDA -> ReporteriaService.soles(deuda);
            case INDICE_MOROSIDAD -> emitido > 0 ? porcentaje(deuda, emitido) : null;
            case PORCENTAJE_COBRADO -> emitido > 0 ? porcentaje(cobrado, emitido) : null;
            case REGULARIDAD_PAGO -> cuotas > 0 ? porcentaje(aTiempo, cuotas) : null;
            case CUOTAS -> cuotas;
            case CONTRIBUYENTES -> contribuyentes;
            case CONTRIBUYENTES_MOROSOS -> morosos;
            case BUENOS_PAGADORES -> buenos;
        };
    }

    private static double porcentaje(long parte, long total) {
        return Math.round(parte * 10000.0 / total) / 100.0;
    }

    private static String describir(ConsultaAsistente q, PreguntaAsistentePeticion p, Carga carga) {
        String indicadores = q.indicadores().stream().map(i -> i.etiqueta).collect(Collectors.joining(", "));
        String agrupacion = q.agrupacion() == Agrupacion.NINGUNO ? ""
                : " · por " + q.agrupacion().name().toLowerCase(Locale.ROOT).replace("anio", "año");
        String periodo = q.anios().isEmpty()
                ? (q.agrupacion() == Agrupacion.ANIO ? " · " + carga.primerMes().getYear() + " a " + carga.fechaCorte().getYear()
                        : " · " + p.desde() + " a " + p.hasta())
                : " · " + q.anios().stream().map(String::valueOf).collect(Collectors.joining(", "));
        List<String> filtros = new ArrayList<>();
        String zona = q.zona() != null ? q.zona() : p.zona();
        String tributo = q.tributo() != null ? q.tributo() : p.tributo();
        String segmento = q.segmento() != null ? q.segmento() : p.segmento();
        if (zona != null) {
            filtros.add("zona " + zona);
        }
        if (tributo != null) {
            filtros.add("tributo " + tributo);
        }
        if (segmento != null) {
            filtros.add("segmento " + segmento);
        }
        String orden = q.orden() == null ? "" : " · orden " + ("desc".equals(q.orden()) ? "de mayor a menor" : "de menor a mayor");
        String limite = q.limite() == null ? "" : " · primeras " + q.limite() + " filas";
        return indicadores + agrupacion + periodo + (filtros.isEmpty() ? "" : " · " + String.join(", ", filtros)) + orden + limite;
    }

    private static String csv(Resultado r) {
        StringBuilder salida = new StringBuilder(String.join(",", r.columnas()));
        r.filas().stream().limit(ConsultaAsistente.LIMITE_MAXIMO).forEach(fila -> salida.append('\n').append(
                fila.stream().map(String::valueOf).collect(Collectors.joining(","))));
        return salida.toString();
    }

    private static String tablaMarkdown(Resultado r) {
        if (r.columnas().size() == 1 && r.filas().size() == 1) {
            return "**" + r.columnas().get(0) + "**: " + formatear(r.filas().get(0).get(0));
        }
        StringBuilder salida = new StringBuilder("| " + String.join(" | ", r.columnas()) + " |\n|");
        salida.append("---|".repeat(r.columnas().size()));
        r.filas().stream().limit(20).forEach(fila -> salida.append("\n| ")
                .append(fila.stream().map(AsistenteReporteriaService::formatear).collect(Collectors.joining(" | ")))
                .append(" |"));
        return salida.toString();
    }

    private static String formatear(Object valor) {
        return valor instanceof Double d ? String.format(Locale.US, "%,.2f", d) : String.valueOf(valor);
    }
}
