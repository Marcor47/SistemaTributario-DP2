package pe.municipalidad.tributario.reporteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;
import tools.jackson.databind.json.JsonMapper;

/*
 * Asistente IA contra un servicio de lenguaje simulado con la misma API: se comprueba qué se le envía y qué se hace con
 * lo que responde. Muestra: Z01 con deuda de 400 y Z02 con deuda de 50, ambas de impuesto predial en 2026.
 */
@PruebaIntegracion
@Transactional
class AsistenteReporteriaTest {

    private static final String TOKEN = "credencial-de-prueba";
    private static final Deque<String> RESPUESTAS = new ArrayDeque<>();
    private static final List<String> SOLICITUDES = new ArrayList<>();
    private static final List<String> CREDENCIALES = new ArrayList<>();
    private static final HttpServer SERVICIO = iniciarServicio();

    @DynamicPropertySource
    static void servicioDeLenguaje(DynamicPropertyRegistry registro) {
        registro.add("tributario.asistente.llm-url", () -> "http://127.0.0.1:" + SERVICIO.getAddress().getPort() + "/");
        registro.add("tributario.asistente.llm-token", () -> TOKEN);
    }

    private static HttpServer iniciarServicio() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/salud", intercambio -> responder(intercambio, "{\"estado\": \"listo\", \"modelo\": \"modelo-prueba\"}"));
            servidor.createContext("/v1/chat/completions", intercambio -> {
                SOLICITUDES.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                CREDENCIALES.add(intercambio.getRequestHeaders().getFirst("Authorization"));
                String texto = RESPUESTAS.isEmpty() ? "" : RESPUESTAS.poll();
                responder(intercambio, new JsonMapper().writeValueAsString(java.util.Map.of("model", "modelo-prueba",
                        "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", texto))))));
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.sendResponseHeaders(200, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoteETLRepository lotes;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() {
        RESPUESTAS.clear();
        SOLICITUDES.clear();
        CREDENCIALES.clear();
        Usuario usuario = usuarios.save(new Usuario("gerencia_asistente", "Gerencia", "gerencia_asistente@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(NombreRol.GERENCIA).orElseThrow(), LocalDateTime.now()));
        sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());

        LocalDate corte = LocalDate.of(2026, 10, 19);
        LoteETL lote = new LoteETL("ps_etl_cobranza", corte, TipoEjecucion.PROGRAMADA, corte.atTime(4, 0));
        lote.publicar(2, 2, corte.atTime(4, 5));
        lotes.saveAndFlush(lote);
        cuota(lote, "SEUD-ASISTENTE-C1", "SEUD-ASISTENTE-P1", "Z01", 400);
        cuota(lote, "SEUD-ASISTENTE-C2", "SEUD-ASISTENTE-P2", "Z02", 50);
    }

    private void cuota(LoteETL lote, String contribuyente, String predio, String zona, int deuda) {
        jdbc.update("INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona) "
                + "VALUES (?, 'NATURAL') ON CONFLICT DO NOTHING", contribuyente);
        Long idPredio = jdbc.queryForObject("INSERT INTO analitica.dim_predio (seudonimo_predio, zona, tipo_predio) "
                + "VALUES (?, ?, 'URBANO') RETURNING id_predio", Long.class, predio, zona);
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota,
                    fecha_vencimiento, monto_emitido, monto_insoluto, monto_pagado, estado_pago)
                VALUES (?, ?, ?, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-02-27', ?, ?, 0, 'VENCIDA')""",
                lote.getIdLote(), contribuyente, idPredio, deuda, deuda);
    }

    private static String consulta(String indicador, String agrupacion, String extra) {
        return "```json\n{\"indicadores\": [\"" + indicador + "\"], \"agrupar_por\": \"" + agrupacion + "\", \"anios\": []"
                + extra + "}\n```";
    }

    private ResultActions preguntar(String pregunta) throws Exception {
        return mvc.perform(post("/api/reporteria/asistente/preguntas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\": \"" + pregunta + "\", \"desde\": \"2026-01\", \"hasta\": \"2026-10\"}")
                .session(sesion));
    }

    @Test
    void informaElEstadoDelServicio() throws Exception {
        mvc.perform(get("/api/reporteria/asistente/estado").session(sesion))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.modelo").value("modelo-prueba"));
    }

    @Test
    void respondeConLosAgregadosYEnviaLaCredencialSinDatosDeIdentidad() throws Exception {
        RESPUESTAS.add(consulta("deuda_vencida", "zona", ", \"orden\": \"desc\", \"limite\": 1"));
        RESPUESTAS.add("La zona con más deuda es Z01, con S/ 400.00.");

        preguntar("Top 1 de zonas con más deuda")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("La zona con más deuda es Z01, con S/ 400.00."))
                .andExpect(jsonPath("$.columnas", contains("Zona", "Deuda vencida")))
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0][0]").value("Z01"))
                .andExpect(jsonPath("$.filas[0][1]").value(400.0))
                .andExpect(jsonPath("$.consulta", containsString("Deuda vencida")))
                .andExpect(jsonPath("$.consultaCorregida").value(false))
                .andExpect(jsonPath("$.respuestaReemplazada").value(false));

        assertThat(CREDENCIALES).containsOnly("Bearer " + TOKEN);
        assertThat(String.join("", SOLICITUDES)).contains("Top 1 de zonas").contains("Z01,400.0")
                .doesNotContain("SEUD-ASISTENTE");
    }

    @Test
    void unaConsultaFueraDelCatalogoNoSeEjecutaYSeCorrigeUnaVez() throws Exception {
        RESPUESTAS.add("```json\n{\"indicadores\": [\"contrasena\"], \"agrupar_por\": \"zona\"}\n```");
        RESPUESTAS.add(consulta("deuda_vencida", "ninguno", ""));
        RESPUESTAS.add("La deuda vencida es S/ 450.00.");

        preguntar("¿Cuánto se debe?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultaCorregida").value(true))
                .andExpect(jsonPath("$.filas[0][0]").value(450.0));

        assertThat(SOLICITUDES.get(1)).contains("no existe");
    }

    @Test
    void siNoLograUnaConsultaValidaNoInventaUnaRespuesta() throws Exception {
        RESPUESTAS.add("DROP TABLE analitica.hecho_cobranza");
        RESPUESTAS.add("SELECT clave_hash FROM seguridad.usuario");

        preguntar("Dame las claves")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta", containsString("No pude armar una consulta")))
                .andExpect(jsonPath("$.filas", hasSize(0)));

        assertThat(SOLICITUDES).hasSize(2);
    }

    @Test
    void rechazaUnaZonaQueNoEstaEnElCatalogo() throws Exception {
        RESPUESTAS.add(consulta("deuda_vencida", "ninguno", ", \"zona\": \"Z01' OR 1=1 --\""));
        RESPUESTAS.add(consulta("deuda_vencida", "ninguno", ", \"zona\": \"Z02\""));
        RESPUESTAS.add("La deuda vencida de Z02 es S/ 50.00.");

        preguntar("Deuda de la zona Z02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultaCorregida").value(true))
                .andExpect(jsonPath("$.filas[0][0]").value(50.0));
    }

    @Test
    void sinFilasNoLePideAlModeloQueRedacte() throws Exception {
        RESPUESTAS.add("```json\n{\"indicadores\": [\"deuda_vencida\"], \"agrupar_por\": \"ninguno\", \"anios\": [2020]}\n```");

        preguntar("Deuda de 2020")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta", containsString("no devolvió datos")));

        assertThat(SOLICITUDES).hasSize(1);
    }

    @Test
    void siInventaCifrasDosVecesSeMuestraElResultado() throws Exception {
        RESPUESTAS.add(consulta("deuda_vencida", "ninguno", ""));
        RESPUESTAS.add("La deuda vencida es S/ 999,999.00.");
        RESPUESTAS.add("La deuda vencida es S/ 888,888.00 frente a 2019.");

        preguntar("¿Cuánto se debe?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuestaReemplazada").value(true))
                .andExpect(jsonPath("$.respuesta", containsString("450.00")))
                .andExpect(jsonPath("$.graficos", hasSize(0)));
    }

    @Test
    void extraeLosGraficosDeLaRespuesta() throws Exception {
        RESPUESTAS.add(consulta("deuda_vencida", "zona", ""));
        RESPUESTAS.add("""
                Z01 concentra S/ 400.00.
                ```grafico
                {"tipo": "barras", "titulo": "Deuda", "categorias": ["Z01", "Z02"], "series": [{"nombre": "Deuda", "valores": [400, 50]}]}
                ```""");

        preguntar("Deuda por zona")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graficos", hasSize(1)))
                .andExpect(jsonPath("$.graficos[0].categorias", contains("Z01", "Z02")));
    }
}
