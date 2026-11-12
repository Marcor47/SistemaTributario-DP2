package pe.municipalidad.tributario.ia;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.AccesoDenegadoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * Mapa de la segmentación (HU030, RF-043): cada contribuyente en el plano de las dos primeras componentes principales,
 * con el color de su segmento. El mapa solo lleva seudónimos e ids de asignación (RNF-018). El nombre aparece en la
 * ficha y en la búsqueda, solo para quien puede consultar contribuyentes, y cada vez queda en la auditoría como
 * REIDENTIFICAR, igual que el detalle de la bandeja de homologación.
 */
@Service
@Transactional(readOnly = true)
public class SegmentacionMapaService {

    static final String PERMISO_REIDENTIFICAR = "CONTRIBUYENTES:CONSULTAR";
    private static final int MAX_RESULTADOS = 20;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final RegistroAuditoriaRepository auditorias;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public SegmentacionMapaService(JdbcTemplate jdbc, JsonMapper json, RegistroAuditoriaRepository auditorias,
            UsuarioRepository usuarios, Clock reloj) {
        this.jdbc = jdbc;
        this.json = json;
        this.auditorias = auditorias;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    public record Variable(String clave, String etiqueta, String unidad) {
    }

    public record ModeloMapa(Long idModelo, String version, String estado, Long idLote, LocalDate fechaCorte,
            int contribuyentes, List<Double> varianzaExplicada) {
    }

    public record SegmentoMapa(Long idSegmento, int indice, String nombre, String descripcion, int tamano,
            String estado, JsonNode variablesCaracteristicas) {
    }

    /* puntos: [idAsignacion, índice del segmento, x, y], compacto para decenas de miles de contribuyentes. */
    public record MapaRespuesta(ModeloMapa modelo, List<Variable> variables, List<SegmentoMapa> segmentos,
            List<Object[]> puntos, boolean puedeVerNombres) {
    }

    public record Contribuyente(String codigo, String nombre) {
    }

    public record FichaRespuesta(Long idAsignacion, String seudonimoCorto, String tipoPersona, Contribuyente contribuyente,
            Long idSegmento, String segmento, Map<String, Double> valores, Map<String, Double> medianasSegmento,
            double x, double y) {
    }

    public record ResultadoBusqueda(Long idAsignacion, String codigo, String nombre, Long idSegmento, String segmento) {
    }

    static final List<Variable> VARIABLES = List.of(
            new Variable("pctATiempo", "Cuotas pagadas a tiempo", "%"),
            new Variable("diasAtraso", "Atraso medio al pagar", "días"),
            new Variable("deudaVencidaPct", "Deuda vencida sobre lo emitido", "%"),
            new Variable("antiguedadDeudaAnios", "Antigüedad de la deuda", "años"),
            new Variable("mesesSinPago", "Meses desde el último pago", "meses"));

    /* Sin idModelo: la segmentación publicada si es de K-means; si no, el último borrador. */
    public MapaRespuesta mapa(Long idModelo, boolean puedeVerNombres) {
        Long id = idModelo != null ? idModelo : jdbc.query("""
                SELECT s.id_modelo FROM analitica.segmento s
                JOIN analitica.modelo_ia m ON m.id_modelo = s.id_modelo
                WHERE m.nombre = ? AND s.estado IN ('PUBLICADO', 'BORRADOR')
                  AND EXISTS (SELECT 1 FROM analitica.asignacion_segmento a
                              WHERE a.id_segmento = s.id_segmento AND a.coord_x IS NOT NULL)
                ORDER BY (s.estado = 'PUBLICADO') DESC, s.id_modelo DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, SegmentacionModeloService.NOMBRE_MODELO);
        if (id == null) {
            throw new RecursoNoEncontradoException("MAPA_NO_DISPONIBLE",
                    "Aún no hay una segmentación con mapa. Calcúlela con POST /api/segmentos/calculos.");
        }
        List<SegmentoMapa> segmentos = new ArrayList<>();
        Map<Long, Integer> indice = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id_segmento, nombre, descripcion, tamano, estado, variables_caracteristicas
                FROM analitica.segmento WHERE id_modelo = ? ORDER BY nombre, id_segmento
                """, rs -> {
                    indice.put(rs.getLong("id_segmento"), segmentos.size());
                    segmentos.add(new SegmentoMapa(rs.getLong("id_segmento"), segmentos.size(), rs.getString("nombre"),
                            rs.getString("descripcion"), rs.getInt("tamano"), rs.getString("estado"),
                            leer(rs.getString("variables_caracteristicas"))));
                }, id);
        List<Object[]> puntos = jdbc.query("""
                SELECT a.id_asignacion, a.id_segmento, a.coord_x, a.coord_y
                FROM analitica.asignacion_segmento a
                JOIN analitica.segmento s ON s.id_segmento = a.id_segmento
                WHERE s.id_modelo = ? AND a.coord_x IS NOT NULL
                ORDER BY a.id_asignacion
                """, (rs, i) -> new Object[] {rs.getLong(1), indice.get(rs.getLong(2)), rs.getDouble(3), rs.getDouble(4)},
                id);
        if (segmentos.isEmpty() || puntos.isEmpty()) {
            throw new RecursoNoEncontradoException("MAPA_NO_DISPONIBLE",
                    "El modelo " + id + " no tiene segmentos con mapa.");
        }
        ModeloMapa modelo = jdbc.queryForObject("""
                SELECT m.id_modelo, m.version,
                       (SELECT CASE WHEN bool_or(s.estado = 'PUBLICADO') THEN 'PUBLICADO' ELSE max(s.estado) END
                        FROM analitica.segmento s WHERE s.id_modelo = m.id_modelo) AS estado,
                       (SELECT t.entrada FROM analitica.traza_ia t WHERE t.id_modelo = m.id_modelo
                        ORDER BY t.id_traza LIMIT 1) AS entrada,
                       (SELECT t.salida FROM analitica.traza_ia t WHERE t.id_modelo = m.id_modelo
                        ORDER BY t.id_traza LIMIT 1) AS salida
                FROM analitica.modelo_ia m WHERE m.id_modelo = ?
                """, (rs, i) -> {
                    JsonNode entrada = leer(rs.getString("entrada"));
                    JsonNode salida = leer(rs.getString("salida"));
                    List<Double> varianza = new ArrayList<>();
                    JsonNode v = salida == null ? null : salida.path("modelo").path("mapa").path("varianzaExplicada");
                    if (v != null) {
                        v.forEach(n -> varianza.add(n.asDouble()));
                    }
                    return new ModeloMapa(rs.getLong("id_modelo"), rs.getString("version"), rs.getString("estado"),
                            entrada == null ? null : entrada.path("idLote").asLong(),
                            entrada == null ? null : LocalDate.parse(entrada.path("fechaCorte").asString()),
                            entrada == null ? 0 : entrada.path("contribuyentes").asInt(), varianza);
                }, id);
        return new MapaRespuesta(modelo, VARIABLES, segmentos, puntos, puedeVerNombres);
    }

    @Transactional
    public FichaRespuesta ficha(Long idAsignacion, boolean puedeVerNombres, String nombreUsuario) {
        Map<String, Object> fila = jdbc.query("""
                SELECT a.id_asignacion, a.seudonimo_contribuyente, a.variables, a.coord_x, a.coord_y,
                       s.id_segmento, s.nombre, s.variables_caracteristicas, d.tipo_persona
                FROM analitica.asignacion_segmento a
                JOIN analitica.segmento s ON s.id_segmento = a.id_segmento
                JOIN analitica.dim_contribuyente d ON d.seudonimo_contribuyente = a.seudonimo_contribuyente
                WHERE a.id_asignacion = ? AND a.coord_x IS NOT NULL
                """, rs -> rs.next() ? Map.of("seudonimo", rs.getString("seudonimo_contribuyente"),
                        "variables", rs.getString("variables"), "x", rs.getDouble("coord_x"), "y", rs.getDouble("coord_y"),
                        "idSegmento", rs.getLong("id_segmento"), "segmento", rs.getString("nombre"),
                        "caracteristicas", rs.getString("variables_caracteristicas"),
                        "tipoPersona", rs.getString("tipo_persona")) : null, idAsignacion);
        if (fila == null) {
            throw new RecursoNoEncontradoException("ASIGNACION_NO_ENCONTRADA",
                    "No existe la asignación " + idAsignacion + " en un mapa de segmentación.");
        }
        String seudonimo = (String) fila.get("seudonimo");
        Contribuyente contribuyente = null;
        if (puedeVerNombres) {
            contribuyente = jdbc.query("""
                    SELECT pc.codigo, pc.nombre FROM maestros.entidad_maestra e
                    JOIN plata.contribuyente pc ON pc.id_maestro = e.id_maestro
                    WHERE e.seudonimo = ? ORDER BY pc.codigo LIMIT 1
                    """, rs -> rs.next() ? new Contribuyente(rs.getString(1), rs.getString(2)) : null, seudonimo);
            auditar(nombreUsuario, "asignacion:" + idAsignacion + (contribuyente == null ? " (sin datos en plata)" : ""));
        }
        Map<String, Double> valores = new LinkedHashMap<>();
        leer((String) fila.get("variables")).properties().forEach(e -> valores.put(e.getKey(), e.getValue().asDouble()));
        Map<String, Double> medianas = new LinkedHashMap<>();
        leer((String) fila.get("caracteristicas")).path("variables").properties()
                .forEach(e -> medianas.put(e.getKey(), e.getValue().path("mediana").asDouble()));
        return new FichaRespuesta(idAsignacion, seudonimo.substring(0, 10), (String) fila.get("tipoPersona"),
                contribuyente, (Long) fila.get("idSegmento"), (String) fila.get("segmento"), valores, medianas,
                (Double) fila.get("x"), (Double) fila.get("y"));
    }

    /* Por código de SAMNET, documento o parte del nombre (sin tildes ni mayúsculas), dentro del mapa del modelo. */
    @Transactional
    public List<ResultadoBusqueda> buscar(Long idModelo, String consulta, boolean puedeVerNombres, String nombreUsuario) {
        if (!puedeVerNombres) {
            throw new AccesoDenegadoException("SIN_PERMISO_REIDENTIFICAR",
                    "Buscar por nombre o documento requiere el permiso " + PERMISO_REIDENTIFICAR + ".");
        }
        String q = consulta == null ? "" : consulta.strip();
        if (q.length() < 3) {
            throw new DatoInvalidoException("CONSULTA_CORTA", "Escriba al menos 3 caracteres.");
        }
        Long id = idModelo != null ? idModelo : mapa(null, true).modelo().idModelo();
        List<ResultadoBusqueda> resultados = jdbc.query("""
                SELECT DISTINCT ON (a.id_asignacion) a.id_asignacion, pc.codigo, pc.nombre, s.id_segmento, s.nombre
                FROM analitica.asignacion_segmento a
                JOIN analitica.segmento s ON s.id_segmento = a.id_segmento
                JOIN maestros.entidad_maestra e ON e.seudonimo = a.seudonimo_contribuyente
                JOIN plata.contribuyente pc ON pc.id_maestro = e.id_maestro
                WHERE s.id_modelo = ? AND a.coord_x IS NOT NULL
                  AND (pc.codigo = ? OR pc.numero_documento LIKE ? || '%'
                       OR etl.texto_comparable(pc.nombre) LIKE '%' || etl.texto_comparable(?) || '%')
                ORDER BY a.id_asignacion, pc.codigo
                LIMIT ?
                """, (rs, i) -> new ResultadoBusqueda(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5)), id, q, q.replaceAll("[\\s.-]", ""), q, MAX_RESULTADOS);
        auditar(nombreUsuario, "busqueda en el mapa del modelo " + id + ": " + resultados.size() + " resultados");
        return resultados;
    }

    private void auditar(String nombreUsuario, String detalle) {
        auditorias.save(new RegistroAuditoria(usuarios.findByNombreUsuarioIgnoreCase(nombreUsuario).orElse(null),
                "REIDENTIFICAR", "AsignacionSegmento", null, detalle + " · " + nombreUsuario,
                LocalDateTime.now(reloj)));
    }

    private JsonNode leer(String texto) {
        return texto == null ? null : json.readTree(texto);
    }
}
