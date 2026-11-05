package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;

/** Misma instalación de semillas que recibirá un compañero al arrancar el perfil local desde una BD vacía. */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba,classpath:db/datos-demo",
        "tributario.test.base=semillas_demo",
        "tributario.niubiz.mode=sandbox", "tributario.niubiz.username=", "tributario.niubiz.password="
})
@AutoConfigureMockMvc
@Import(PostgresEmbebidoConfig.class)
class SemillasDemoTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void gerenciaRecibeDatosDelDashboardYSusCuatroFiltros() throws Exception {
        var login = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"gerencia\",\"clave\":\"gerencia123\"}"))
                .andExpect(status().isOk()).andReturn();
        var interna = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/dashboard").session(interna)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorteCarga").value("2026-11-04"))
                .andExpect(jsonPath("$.resumen.montoEmitido").isNumber())
                .andExpect(jsonPath("$.porTributo", hasSize(2)))
                .andExpect(jsonPath("$.porSegmento").isNotEmpty())
                .andExpect(jsonPath("$.avanceMetas", hasSize(3)));
        mvc.perform(get("/api/dashboard/filtros").session(interna)).andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas").isNotEmpty()).andExpect(jsonPath("$.segmentos", hasSize(3)))
                .andExpect(jsonPath("$.anios[0]").value(2026));
        mvc.perform(get("/api/dashboard").session(interna).param("zona", "Z2").param("tributo", "ARBITRIOS")
                .param("anio", "2026").param("numeroPeriodo", "11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.porZona", hasSize(1)))
                .andExpect(jsonPath("$.porTributo", hasSize(1)));
    }

    @Test
    void buzonDeEjemploDescargaPdfYRepetirLasSemillasConservaLaPrimeraLectura() throws Exception {
        var propia = sesion("061177");
        long id = jdbc.queryForObject("SELECT id_documento FROM operativo.documento_buzon "
                + "WHERE numero_constancia='CNA-DEMO-HU019-01'", Long.class);
        mvc.perform(get("/api/buzon").session(propia)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.sinLeer").value(2));
        byte[] contenido = mvc.perform(get("/api/buzon/" + id + "/descarga").session(propia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var pdf = Loader.loadPDF(contenido)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("DOCUMENTO DE PRUEBA", "061177", "No acredita");
        }
        mvc.perform(get("/api/buzon/" + id).session(sesion("021390"))).andExpect(status().isNotFound());
        var primeraLectura = jdbc.queryForObject("SELECT fecha_primera_lectura FROM operativo.documento_buzon "
                + "WHERE id_documento=?", java.sql.Timestamp.class, id);
        var antes = conteos();
        for (int repeticion = 0; repeticion < 2; repeticion++) {
            ejecutar("db/datos-demo/R__zz_demo_01_dashboard.sql");
            ejecutar("db/datos-demo/R__zz_demo_02_buzon.sql");
        }
        assertThat(conteos()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT fecha_primera_lectura FROM operativo.documento_buzon "
                + "WHERE id_documento=?", java.sql.Timestamp.class, id)).isEqualTo(primeraLectura);
    }

    private List<Long> conteos() {
        return List.of("analitica.lote_etl", "analitica.hecho_cobranza", "analitica.dim_contribuyente",
                "analitica.dim_predio", "analitica.asignacion_segmento", "operativo.documento_buzon",
                "operativo.solicitud_constancia", "operativo.solicitud_predio", "samnet.pago").stream()
                .map(tabla -> jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class)).toList();
    }

    @Test
    void cuentaNiubizTieneUnaCuotaYLaSemillaNoReabreUnaDeudaPagada() throws Exception {
        var acceso = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"099023\",\"password\":\"Portal099023!\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(acceso.getRequest().getSession(false)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT sum(monto_emitido - monto_pagado) FROM samnet.obligacion "
                + "WHERE contribuyente_codigo='099024'", java.math.BigDecimal.class))
                .isEqualByComparingTo("1.00");
        assertThat(jdbc.queryForObject("SELECT sum(monto_emitido - monto_pagado) FROM samnet.obligacion "
                + "WHERE contribuyente_codigo='099023'", java.math.BigDecimal.class))
                .isEqualByComparingTo("1.00");
        try {
            jdbc.update("UPDATE samnet.obligacion SET monto_pagado=monto_emitido WHERE contribuyente_codigo='099023'");
            ejecutar("db/datos-demo/R__zz_demo_03_pago_niubiz.sql");
            assertThat(jdbc.queryForObject("SELECT sum(monto_emitido - monto_pagado) FROM samnet.obligacion "
                    + "WHERE contribuyente_codigo='099023'", java.math.BigDecimal.class))
                    .isEqualByComparingTo("0.00");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.obligacion WHERE contribuyente_codigo='099023'", Long.class))
                    .isEqualTo(1L);
        } finally {
            jdbc.update("UPDATE samnet.obligacion SET monto_pagado=0 WHERE contribuyente_codigo='099023'");
        }
    }
    private void ejecutar(String ruta) throws Exception {
        try (var entrada = new ClassPathResource(ruta).getInputStream()) {
            jdbc.execute(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private MockHttpSession sesion(String codigo) {
        var sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigo);
        return sesion;
    }
}
