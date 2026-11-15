package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;

/** Misma instalación de semillas que recibe un compañero al arrancar el perfil local desde una BD vacía. */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba,classpath:db/datos-padron",
        "tributario.test.base=semillas_padron", "spring.flyway.placeholders.volumen_padron=" + SemillasPadronTest.VOLUMEN,
        "tributario.niubiz.mode=sandbox", "tributario.niubiz.username=", "tributario.niubiz.password="
})
@AutoConfigureMockMvc
@Import(PostgresEmbebidoConfig.class)
class SemillasPadronTest {

    static final String VOLUMEN = "120";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void gerenciaConsultaLaPlataformaConLaCargaDelPadron() throws Exception {
        var login = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"gerencia\",\"clave\":\"gerencia123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("María Fernanda Quispe Rojas")).andReturn();
        var interna = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/reporteria/catalogo").session(interna)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorte").value("2026-11-13"));
        mvc.perform(get("/api/reporteria/datos").session(interna).param("desde", "2026-01").param("hasta", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.emitido").isNumber());
    }

    @Test
    void elPadronTieneVolumenTramitesEnSusEstadosYNingunRastroDeDesarrollo() throws Exception {
        assertThat(contar("samnet.contribuyente WHERE codigo ~ '^2[0-9]{5}$'")).isGreaterThan(100);
        assertThat(contar("samnet.predio p JOIN samnet.predio_titular t USING (codigo_predio) "
                + "WHERE t.contribuyente_codigo ~ '^2[0-9]{5}$'")).isGreaterThan(100);
        assertThat(contar("samnet.obligacion WHERE anio BETWEEN 2022 AND 2026")).isGreaterThan(5000);
        assertThat(contar("operativo.reclamo")).isPositive();
        assertThat(contar("operativo.solicitud_constancia")).isPositive();
        assertThat(contar("operativo.solicitud_predio")).isPositive();
        assertThat(contar("operativo.constancia WHERE numero_constancia !~ '^CND-[0-9]{4}-[2-9A-HJKMNP-Z]{6}$'"))
                .isZero();
        assertThat(contar("operativo.constancia WHERE firma_local")).isZero();
        assertThat(contar("analitica.modelo_ia WHERE nombre ILIKE '%demo%'")).isZero();
        assertThat(contar("analitica.segmento WHERE nombre ILIKE '%demo%'")).isZero();
        assertThat(contar("seguridad.usuario WHERE nombre_completo ILIKE '%prueba%' OR correo ILIKE '%example%'"))
                .isZero();
        assertThat(contar("samnet.contribuyente WHERE correo LIKE '%.test' OR correo ILIKE '%example%'")).isZero();
        /* Al menos tres usuarios por cada rol interno. */
        assertThat(jdbc.queryForList("""
                SELECT r.nombre FROM seguridad.rol r
                LEFT JOIN seguridad.usuario_rol ur ON ur.id_rol = r.id_rol
                GROUP BY r.nombre HAVING count(ur.id_usuario) < 3
                """, String.class)).isEmpty();
    }

    @Test
    void unContribuyenteDelPadronIngresaConSuClaveInicial() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"200001\",\"password\":\"SanMiguel200001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("200001"))
                .andExpect(jsonPath("$.nombre").isNotEmpty());
    }

    @Test
    void repetirElPadronDaLosMismosDatos() throws Exception {
        var antes = conteos();
        ejecutar("db/datos-padron/R__zz_padron_10_padron_san_miguel.sql");
        assertThat(conteos()).isEqualTo(antes);
    }

    private List<Long> conteos() {
        return List.of("samnet.contribuyente", "samnet.predio", "samnet.obligacion", "samnet.pago",
                "operativo.reclamo", "operativo.solicitud_constancia", "operativo.solicitud_predio",
                "operativo.documento_buzon").stream().map(this::contar).toList();
    }

    private long contar(String desde) {
        return jdbc.queryForObject("SELECT count(*) FROM " + desde, Long.class);
    }

    private void ejecutar(String ruta) throws Exception {
        try (var entrada = new ClassPathResource(ruta).getInputStream()) {
            jdbc.execute(new String(entrada.readAllBytes(), StandardCharsets.UTF_8).replace("${volumen_padron}", VOLUMEN));
        }
    }
}
