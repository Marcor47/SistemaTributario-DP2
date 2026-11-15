package pe.municipalidad.tributario.reporteria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.ia.ModeloIA;
import pe.municipalidad.tributario.ia.ModeloIARepository;
import pe.municipalidad.tributario.ia.ResumenEjecutivo;
import pe.municipalidad.tributario.ia.ResumenEjecutivoRepository;
import pe.municipalidad.tributario.ia.TipoModeloIA;
import pe.municipalidad.tributario.obligacion.Tributo;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Agregado;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Carga;
import pe.municipalidad.tributario.reporteria.CuboReporteria.Filtro;
import pe.municipalidad.tributario.reporteria.ModeloLenguajeCliente.Completado;
import pe.municipalidad.tributario.reporteria.ModeloLenguajeCliente.Mensaje;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/*
 * HU017 (RF-023): resumen ejecutivo de un mes redactado por el modelo de lenguaje solo con indicadores calculados de
 * la carga publicada. Se consulta al modelo una vez por periodo y carga: si ya existe un resumen, se devuelve el
 * guardado. Si el modelo agrega cifras que no están en los indicadores, se guarda un texto armado con los indicadores
 * en vez de reintentar, para no gastar consultas.
 */
@Service
public class ResumenEjecutivoService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumenEjecutivoService.class);
    private static final int TOKENS_RESUMEN = 320;
    private static final String VERSION_SERVICIO = "servicio-local";
    private static final String FORMATO_MODELO = "SAFETENSORS";
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
        "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String SISTEMA = """
            Eres analista de la Gerencia de Rentas de la Municipalidad Distrital de San Miguel (Lima, Perú). Redactas \
            resúmenes ejecutivos en español formal, en un solo párrafo de 90 a 140 palabras, sin títulos ni viñetas. \
            Usa solo las cifras de la lista, escritas igual que en ella. No inventes cifras, causas ni proyecciones.""";

    public record IndicadorUsado(String codigo, String nombre, BigDecimal valor, String unidad) {
    }

    public record ResumenRespuesta(Long idResumen, String periodo, String texto, List<IndicadorUsado> indicadoresUsados,
            String estado, LocalDateTime fechaGeneracion, String revisor, boolean generadoPorModelo) {
    }

    private final CuboReporteria cubo;
    private final ModeloLenguajeCliente modelo;
    private final ResumenEjecutivoRepository resumenes;
    private final ModeloIARepository modelos;
    private final UsuarioRepository usuarios;
    private final JsonMapper json;
    private final Clock reloj;

    public ResumenEjecutivoService(CuboReporteria cubo, ModeloLenguajeCliente modelo,
            ResumenEjecutivoRepository resumenes, ModeloIARepository modelos, UsuarioRepository usuarios,
            JsonMapper json, Clock reloj) {
        this.cubo = cubo;
        this.modelo = modelo;
        this.resumenes = resumenes;
        this.modelos = modelos;
        this.usuarios = usuarios;
        this.json = json;
        this.reloj = reloj;
    }

    @Transactional
    public ResumenRespuesta generar(String periodo) {
        YearMonth mes = leerPeriodo(periodo);
        Carga carga = cubo.carga();
        if (mes.isBefore(carga.primerMes()) || mes.isAfter(YearMonth.from(carga.fechaCorte()))) {
            throw new ReglaNegocioException("PERIODO_SIN_INDICADORES",
                    "La carga publicada no tiene indicadores para " + nombreMes(mes) + ".");
        }
        var guardado = resumenes.findFirstByPeriodoAndIdLoteOrderByFechaGeneracionDesc(periodo, carga.idLote());
        if (guardado.isPresent()) {
            return respuesta(guardado.get(), true);
        }
        List<IndicadorUsado> indicadores = indicadores(carga, mes);
        if (indicadores.isEmpty()) {
            throw new ReglaNegocioException("PERIODO_SIN_INDICADORES",
                    "El periodo " + nombreMes(mes) + " no tiene cuotas emitidas en la carga publicada.");
        }
        Completado completado = modelo.completar(List.of(new Mensaje("system", SISTEMA),
                new Mensaje("user", "Periodo: " + nombreMes(mes) + ". Indicadores calculados:\n" + lista(indicadores)
                        + "\nRedacta el resumen ejecutivo del periodo para la Gerencia.")), TOKENS_RESUMEN);
        String texto = limpiar(completado.texto());
        boolean delModelo = !texto.isBlank() && palabras(texto) <= ResumenEjecutivo.MAXIMO_PALABRAS
                && VerificadorCifras.inventadas(texto, List.of(indicadores.stream()
                        .map(i -> (Object) i.valor()).toList()), List.of(nombreMes(mes))).isEmpty();
        if (!delModelo) {
            LOG.warn("El resumen de {} del modelo no pasó la verificación de cifras; se usa el texto de indicadores",
                    periodo);
            texto = textoDeIndicadores(mes, indicadores);
        }
        ModeloIA registro = modeloRegistrado(completado.modelo());
        ResumenEjecutivo resumen = resumenes.save(new ResumenEjecutivo(registro, periodo, texto,
                escribir(indicadores), carga.idLote(), LocalDateTime.now(reloj)));
        return respuesta(resumen, delModelo);
    }

    @Transactional
    public ResumenRespuesta revisar(Long idResumen, String nombreUsuario) {
        ResumenEjecutivo resumen = buscar(idResumen);
        Usuario revisor = usuarios.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO", "Usuario no encontrado."));
        resumen.revisar(revisor);
        return respuesta(resumen, true);
    }

    @Transactional(readOnly = true)
    public ResumenEjecutivo paraDescarga(Long idResumen) {
        ResumenEjecutivo resumen = buscar(idResumen);
        resumen.descargar();
        if (resumen.getRevisor() != null) {
            resumen.getRevisor().getNombreCompleto();
        }
        return resumen;
    }

    public List<IndicadorUsado> indicadoresDe(ResumenEjecutivo resumen) {
        try {
            return json.readValue(resumen.getIndicadoresUsados(), new TypeReference<List<IndicadorUsado>>() { });
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private ResumenEjecutivo buscar(Long idResumen) {
        return resumenes.findById(idResumen).orElseThrow(() -> new RecursoNoEncontradoException(
                "RESUMEN_NO_ENCONTRADO", "No existe el resumen ejecutivo solicitado."));
    }

    private List<IndicadorUsado> indicadores(Carga carga, YearMonth mes) {
        Agregado a = cubo.calcular(carga, new Filtro(mes, mes, null, null, null, null, null));
        if (a.cuotas == 0) {
            return List.of();
        }
        List<IndicadorUsado> lista = new ArrayList<>();
        lista.add(soles("MONTO_EMITIDO", "Monto emitido del mes", a.emitido));
        lista.add(soles("MONTO_COBRADO", "Monto cobrado de la emisión del mes", a.cobrado));
        lista.add(porcentaje("PORC_RECAUDACION_EMISION", "Recaudación respecto de la emisión", a.cobrado, a.emitido));
        lista.add(soles("DEUDA_VENCIDA", "Deuda vencida sin pagar", a.deuda));
        lista.add(porcentaje("INDICE_MOROSIDAD", "Índice de morosidad", a.deuda, a.emitido));
        lista.add(cantidad("CONTRIBUYENTES_ACTIVOS", "Contribuyentes con cuotas emitidas", a.contribuyentes));
        lista.add(cantidad("CONTRIBUYENTES_MOROSOS", "Contribuyentes con deuda vencida", a.morosos));
        List<Tributo> tributos = CuboReporteria.tributos();
        for (int t = 0; t < tributos.size(); t++) {
            if (a.porTributo.emitido[t] > 0) {
                lista.add(porcentaje("INDICE_MOROSIDAD_" + tributos.get(t).name(),
                        "Índice de morosidad de " + tributos.get(t).getDescripcion(), a.porTributo.deuda[t],
                        a.porTributo.emitido[t]));
            }
        }
        int peor = -1;
        double peorTasa = -1;
        for (int z = 0; z < carga.zonas().size(); z++) {
            if (a.porZona.emitido[z] > 0 && !CuboReporteria.SIN_ZONA.equals(carga.zonas().get(z))) {
                double tasa = (double) a.porZona.deuda[z] / a.porZona.emitido[z];
                if (tasa > peorTasa) {
                    peorTasa = tasa;
                    peor = z;
                }
            }
        }
        if (peor >= 0) {
            lista.add(porcentaje("INDICE_MOROSIDAD_ZONA_MAYOR",
                    "Índice de morosidad de la zona " + carga.zonas().get(peor) + " (la más alta)",
                    a.porZona.deuda[peor], a.porZona.emitido[peor]));
        }
        YearMonth enero = YearMonth.of(mes.getYear(), 1);
        YearMonth inicio = enero.isBefore(carga.primerMes()) ? carga.primerMes() : enero;
        Agregado anual = cubo.calcular(carga, new Filtro(inicio, mes, null, null, null, null, null));
        lista.add(porcentaje("PORC_RECAUDACION_ACUMULADA", "Recaudación acumulada del año respecto de la emisión",
                anual.cobrado, anual.emitido));
        return lista;
    }

    private static IndicadorUsado soles(String codigo, String nombre, long centimos) {
        return new IndicadorUsado(codigo, nombre, BigDecimal.valueOf(centimos, 2), "SOLES");
    }

    private static IndicadorUsado porcentaje(String codigo, String nombre, long parte, long total) {
        BigDecimal valor = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new IndicadorUsado(codigo, nombre, valor, "PORCENTAJE");
    }

    private static IndicadorUsado cantidad(String codigo, String nombre, long valor) {
        return new IndicadorUsado(codigo, nombre, BigDecimal.valueOf(valor), "CANTIDAD");
    }

    /* Las cifras van como el modelo debe escribirlas: S/ con dos decimales y porcentajes con dos decimales. */
    private static String lista(List<IndicadorUsado> indicadores) {
        return indicadores.stream().map(i -> "- " + i.nombre() + ": " + cifra(i)).collect(Collectors.joining("\n"));
    }

    static String cifra(IndicadorUsado i) {
        return switch (i.unidad()) {
            case "SOLES" -> "S/ " + String.format(Locale.US, "%,.2f", i.valor());
            case "PORCENTAJE" -> String.format(Locale.US, "%.2f", i.valor()) + " %";
            default -> String.format(Locale.US, "%,d", i.valor().longValue());
        };
    }

    private static String textoDeIndicadores(YearMonth mes, List<IndicadorUsado> indicadores) {
        StringBuilder texto = new StringBuilder("En ").append(nombreMes(mes)).append(" se emitieron ")
                .append(cifra(indicadores.get(0))).append(" y se cobraron ").append(cifra(indicadores.get(1)))
                .append(" de esa emisión, una recaudación de ").append(cifra(indicadores.get(2)))
                .append(". La deuda vencida sin pagar asciende a ").append(cifra(indicadores.get(3)))
                .append(", con un índice de morosidad de ").append(cifra(indicadores.get(4))).append("; ")
                .append(cifra(indicadores.get(6))).append(" de ").append(cifra(indicadores.get(5)))
                .append(" contribuyentes con cuotas del mes mantienen deuda vencida.");
        for (IndicadorUsado i : indicadores.subList(7, indicadores.size())) {
            texto.append(' ').append(i.nombre()).append(": ").append(cifra(i)).append('.');
        }
        return texto.toString();
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.replaceAll("(?m)^#+\\s*", "").replace("**", "").strip();
    }

    private static int palabras(String texto) {
        return texto.isBlank() ? 0 : texto.trim().split("\\s+").length;
    }

    static String nombreMes(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1] + " de " + mes.getYear();
    }

    private static YearMonth leerPeriodo(String periodo) {
        try {
            return YearMonth.parse(periodo);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new ReglaNegocioException("PERIODO_INVALIDO", "El periodo debe tener el formato AAAA-MM.");
        }
    }

    private ModeloIA modeloRegistrado(String nombre) {
        String nombreModelo = nombre == null || nombre.isBlank() ? "modelo-de-lenguaje-local" : nombre;
        return modelos.findByNombreAndVersion(nombreModelo, VERSION_SERVICIO)
                .orElseGet(() -> modelos.save(new ModeloIA(nombreModelo, TipoModeloIA.RESUMEN_EJECUTIVO,
                        VERSION_SERVICIO, sha256(nombreModelo), FORMATO_MODELO, LocalDateTime.now(reloj))));
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String escribir(List<IndicadorUsado> indicadores) {
        try {
            return json.writeValueAsString(indicadores);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudieron registrar los indicadores del resumen", ex);
        }
    }

    private ResumenRespuesta respuesta(ResumenEjecutivo r, boolean generadoPorModelo) {
        return new ResumenRespuesta(r.getIdResumen(), r.getPeriodo(), r.getTexto(), indicadoresDe(r),
                r.getEstado().name(), r.getFechaGeneracion(),
                r.getRevisor() == null ? null : r.getRevisor().getNombreCompleto(), generadoPorModelo);
    }
}
