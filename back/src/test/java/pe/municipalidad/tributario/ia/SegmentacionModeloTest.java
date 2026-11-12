package pe.municipalidad.tributario.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaInterna;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * HU030 (RF-043): K-means sobre el comportamiento de pago de la carga del ETL de prueba (corte 2026-10-19). Los
 * segmentos quedan en borrador y siguen el flujo de aprobación de HU031. Cada test deshace lo que cambia.
 */
@PruebaInterna
@Transactional
class SegmentacionModeloTest {

    private static final String RUTA = "/api/segmentos/calculos";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private ResultActions calcular() throws Exception {
        return mvc.perform(post(RUTA).with(csrf())
                .with(user("datos").authorities(new SimpleGrantedAuthority("ETL:GESTIONAR"))));
    }

    private long loteVigente() {
        return jdbc.queryForObject("""
                SELECT l.id_lote FROM analitica.lote_etl l
                WHERE l.estado = 'PUBLICADO' AND l.fuente = 'ps_etl_cobranza'
                  AND EXISTS (SELECT 1 FROM analitica.hecho_cobranza h WHERE h.id_lote = l.id_lote)
                ORDER BY l.fecha_corte DESC, l.id_lote DESC LIMIT 1
                """, Long.class);
    }

    @Test
    void cadaContribuyenteConCuotasVencidasRecibeUnSegmentoEnBorrador() throws Exception {
        long lote = loteVigente();
        JsonNode r = json.readTree(calcular()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nuevo").value(true))
                .andExpect(jsonPath("$.idLote").value(lote))
                .andExpect(jsonPath("$.segmentos[*].estado", everyItem(org.hamcrest.Matchers.is("BORRADOR"))))
                .andExpect(jsonPath("$.segmentos[*].nombre", everyItem(startsWith("S"))))
                .andReturn().getResponse().getContentAsString());

        long idModelo = r.get("idModelo").asLong();
        int k = r.get("k").asInt();
        int contribuyentes = r.get("contribuyentes").asInt();
        assertThat(k).isBetween(2, 8);
        assertThat(r.get("segmentos")).hasSize(k);

        Integer conVencidas = jdbc.queryForObject("""
                SELECT count(DISTINCT seudonimo_contribuyente) FROM analitica.hecho_cobranza
                WHERE id_lote = ? AND fecha_vencimiento <= (SELECT fecha_corte FROM analitica.lote_etl WHERE id_lote = ?)
                """, Integer.class, lote, lote);
        assertThat(contribuyentes).isEqualTo(conVencidas);
        List<Map<String, Object>> asignaciones = jdbc.queryForList("""
                SELECT a.seudonimo_contribuyente, count(*) AS veces FROM analitica.asignacion_segmento a
                JOIN analitica.segmento s ON s.id_segmento = a.id_segmento
                WHERE s.id_modelo = ? GROUP BY 1
                """, idModelo);
        assertThat(asignaciones).hasSize(contribuyentes).allMatch(f -> ((Number) f.get("veces")).intValue() == 1);
        Integer sumaTamanos = jdbc.queryForObject(
                "SELECT sum(tamano)::int FROM analitica.segmento WHERE id_modelo = ?", Integer.class, idModelo);
        assertThat(sumaTamanos).isEqualTo(contribuyentes);

        // El k elegido es el de mejor silueta entre los que respetan el tamaño mínimo.
        double mejor = -2;
        for (JsonNode e : r.get("evaluacion")) {
            if (e.get("valido").asBoolean()) {
                mejor = Math.max(mejor, e.get("silueta").asDouble());
            }
        }
        assertThat(r.get("silueta").asDouble()).isEqualTo(mejor);

        // Cada segmento trae sus variables: mediana en unidades reales y centro estandarizado.
        JsonNode variables = json.readTree(r.get("segmentos").get(0).get("variablesCaracteristicas").asString())
                .get("variables");
        assertThat(variables.propertyNames()).containsExactlyElementsOf(SegmentacionModeloService.VARIABLES);
        assertThat(variables.get("pctATiempo").get("unidad").asString()).isEqualTo("%");
    }

    @Test
    void elModeloQuedaRegistradoConSuHashYUnaTrazaInalterable() throws Exception {
        JsonNode r = json.readTree(calcular().andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long idModelo = r.get("idModelo").asLong();

        Map<String, Object> modelo = jdbc.queryForMap(
                "SELECT nombre, tipo, version, hash_integridad, formato, ejecucion_local FROM analitica.modelo_ia"
                        + " WHERE id_modelo = ?", idModelo);
        assertThat(modelo).containsEntry("nombre", SegmentacionModeloService.NOMBRE_MODELO)
                .containsEntry("tipo", "SEGMENTACION").containsEntry("formato", "JSON")
                .containsEntry("ejecucion_local", true);

        Map<String, Object> traza = jdbc.queryForMap(
                "SELECT entrada, salida FROM analitica.traza_ia WHERE id_modelo = ?", idModelo);
        JsonNode entrada = json.readTree((String) traza.get("entrada"));
        assertThat(entrada.get("solicitante").asString()).isEqualTo("datos");
        assertThat(entrada.get("contribuyentes").asInt()).isEqualTo(r.get("contribuyentes").asInt());
        // El hash es el del modelo guardado en la traza (centros, escalado y parámetros), así que se puede verificar.
        String artefacto = json.writeValueAsString(json.readTree((String) traza.get("salida")).get("modelo"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(artefacto.getBytes(StandardCharsets.UTF_8)));
        assertThat(modelo.get("hash_integridad")).isEqualTo(hash);
        assertThat((String) modelo.get("version")).isEqualTo("L" + r.get("idLote").asLong() + "-" + hash.substring(0, 10));
    }

    @Test
    void laMismaCargaDaElMismoModeloSinDuplicar() throws Exception {
        JsonNode primero = json.readTree(calcular().andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        calcular()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevo").value(false))
                .andExpect(jsonPath("$.idModelo").value(primero.get("idModelo").asLong()))
                .andExpect(jsonPath("$.segmentos.length()").value(primero.get("k").asInt()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analitica.modelo_ia WHERE nombre = ?", Integer.class,
                SegmentacionModeloService.NOMBRE_MODELO)).isEqualTo(1);
    }

    @Test
    void reemplazaLosBorradoresAnterioresPeroNoLoPublicado() throws Exception {
        long anterior = jdbc.queryForObject("""
                INSERT INTO analitica.modelo_ia (nombre, tipo, version, hash_integridad, formato, fecha_registro)
                VALUES (?, 'SEGMENTACION', 'L0-anterior', repeat('b', 64), 'JSON', TIMESTAMP '2026-10-01 08:00')
                RETURNING id_modelo
                """, Long.class, SegmentacionModeloService.NOMBRE_MODELO);
        long borrador = segmento(anterior, "Borrador anterior", "BORRADOR");
        long publicado = jdbc.queryForObject("""
                INSERT INTO analitica.segmento (id_modelo, nombre, descripcion, tamano, variables_caracteristicas,
                                                estado, id_aprobador, fecha_aprobacion)
                VALUES (?, 'Publicado', 'x', 0, '{}', 'PUBLICADO',
                        (SELECT id_usuario FROM seguridad.usuario ORDER BY id_usuario LIMIT 1), TIMESTAMP '2026-10-02 08:00')
                RETURNING id_segmento
                """, Long.class, anterior);

        calcular().andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM analitica.segmento WHERE id_segmento = ?",
                Integer.class, borrador)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM analitica.segmento WHERE id_segmento = ?",
                String.class, publicado)).isEqualTo("PUBLICADO");
    }

    @Test
    void laJefaturaApruebaLaSegmentacionCalculada() throws Exception {
        JsonNode r = json.readTree(calcular().andReturn().getResponse().getContentAsString());
        StringBuilder ids = new StringBuilder();
        r.get("segmentos").forEach(s -> ids.append(ids.isEmpty() ? "" : ",").append(s.get("idSegmento").asLong()));

        mvc.perform(post("/api/segmentos/aprobaciones").with(csrf())
                        .with(user("gerencia").authorities(new SimpleGrantedAuthority("SEGMENTOS:APROBAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsSegmento\": [" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicados.length()").value(r.get("k").asInt()))
                .andExpect(jsonPath("$.publicados[0].estado").value("PUBLICADO"));
    }

    @Test
    void soloQuienGestionaElEtlCalcula() throws Exception {
        mvc.perform(post(RUTA).with(csrf())
                        .with(user("gerencia").authorities(new SimpleGrantedAuthority("SEGMENTOS:APROBAR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA).with(csrf())
                        .with(user("analista").authorities(new SimpleGrantedAuthority("SEGMENTOS:CONSULTAR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA).with(user("datos").authorities(new SimpleGrantedAuthority("ETL:GESTIONAR"))))
                .andExpect(status().isForbidden());
    }

    private static final SimpleGrantedAuthority CONSULTAR = new SimpleGrantedAuthority("SEGMENTOS:CONSULTAR");
    private static final SimpleGrantedAuthority REIDENTIFICAR = new SimpleGrantedAuthority("CONTRIBUYENTES:CONSULTAR");

    private int auditoriasReidentificar() {
        return jdbc.queryForObject("SELECT count(*) FROM seguridad.registro_auditoria WHERE accion = 'REIDENTIFICAR'"
                + " AND entidad = 'AsignacionSegmento'", Integer.class);
    }

    @Test
    void elMapaTraeUnPuntoPorContribuyenteSinDatosPersonales() throws Exception {
        JsonNode calculo = json.readTree(calcular().andReturn().getResponse().getContentAsString());
        JsonNode mapa = json.readTree(mvc.perform(get("/api/segmentos/mapa").with(user("gerencia").authorities(CONSULTAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo.idModelo").value(calculo.get("idModelo").asLong()))
                .andExpect(jsonPath("$.modelo.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.modelo.varianzaExplicada.length()").value(2))
                .andExpect(jsonPath("$.variables.length()").value(5))
                .andExpect(jsonPath("$.puedeVerNombres").value(false))
                .andReturn().getResponse().getContentAsString());

        assertThat(mapa.get("puntos")).hasSize(calculo.get("contribuyentes").asInt());
        assertThat(mapa.get("segmentos")).hasSize(calculo.get("k").asInt());
        mapa.get("puntos").forEach(p -> {
            assertThat(p).hasSize(4);
            assertThat(p.get(1).asInt()).isBetween(0, calculo.get("k").asInt() - 1);
        });
        assertThat(mapa.toString()).doesNotContainPattern("[0-9a-f]{64}");
    }

    @Test
    void laFichaMuestraElNombreSoloConPermisoYQuedaAuditada() throws Exception {
        calcular();
        long asignacion = jdbc.queryForObject("""
                SELECT a.id_asignacion FROM analitica.asignacion_segmento a
                JOIN maestros.entidad_maestra e ON e.seudonimo = a.seudonimo_contribuyente
                JOIN plata.contribuyente pc ON pc.id_maestro = e.id_maestro
                WHERE a.coord_x IS NOT NULL ORDER BY a.id_asignacion LIMIT 1
                """, Long.class);
        int antes = auditoriasReidentificar();

        mvc.perform(get("/api/segmentos/mapa/asignaciones/{id}", asignacion).with(user("gerencia").authorities(CONSULTAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente").doesNotExist())
                .andExpect(jsonPath("$.valores.pctATiempo").isNumber())
                .andExpect(jsonPath("$.medianasSegmento.mesesSinPago").isNumber());
        assertThat(auditoriasReidentificar()).isEqualTo(antes);

        mvc.perform(get("/api/segmentos/mapa/asignaciones/{id}", asignacion)
                        .with(user("datos").authorities(CONSULTAR, REIDENTIFICAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.nombre").isString())
                .andExpect(jsonPath("$.contribuyente.codigo").isString());
        assertThat(auditoriasReidentificar()).isEqualTo(antes + 1);
    }

    @Test
    void laBusquedaExigePermisoYEncuentraPorCodigoYNombre() throws Exception {
        calcular();
        Map<String, Object> uno = jdbc.queryForMap("""
                SELECT a.id_asignacion, pc.codigo, pc.nombre FROM analitica.asignacion_segmento a
                JOIN maestros.entidad_maestra e ON e.seudonimo = a.seudonimo_contribuyente
                JOIN plata.contribuyente pc ON pc.id_maestro = e.id_maestro
                WHERE a.coord_x IS NOT NULL ORDER BY a.id_asignacion LIMIT 1
                """);

        mvc.perform(get("/api/segmentos/mapa/busqueda").param("q", (String) uno.get("codigo"))
                        .with(user("gerencia").authorities(CONSULTAR)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/segmentos/mapa/busqueda").param("q", "ab")
                        .with(user("datos").authorities(CONSULTAR, REIDENTIFICAR)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/segmentos/mapa/busqueda").param("q", (String) uno.get("codigo"))
                        .with(user("datos").authorities(CONSULTAR, REIDENTIFICAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idAsignacion").value(((Number) uno.get("id_asignacion")).longValue()));
        // Por una palabra del nombre, en minúsculas y sin tildes.
        String palabra = java.text.Normalizer.normalize(((String) uno.get("nombre")).split("[ ,]+")[0], java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
        mvc.perform(get("/api/segmentos/mapa/busqueda").param("q", palabra)
                        .with(user("datos").authorities(CONSULTAR, REIDENTIFICAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idAsignacion == " + uno.get("id_asignacion") + ")]").exists());
    }

    @Test
    void sinSegmentacionCalculadaNoHayMapa() throws Exception {
        mvc.perform(get("/api/segmentos/mapa").with(user("gerencia").authorities(CONSULTAR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("MAPA_NO_DISPONIBLE"));
    }

    private long segmento(long idModelo, String nombre, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO analitica.segmento (id_modelo, nombre, descripcion, tamano, variables_caracteristicas, estado)
                VALUES (?, ?, 'x', 0, '{}', ?) RETURNING id_segmento
                """, Long.class, idModelo, nombre, estado);
    }
}
