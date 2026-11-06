package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;

/** El modo por defecto permite entrar a Gerencia aunque Niubiz todavía no esté configurado. */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba",
        "tributario.niubiz.mode=sandbox", "tributario.niubiz.username=", "tributario.niubiz.password=",
        "tributario.niubiz.merchant-id=", "tributario.niubiz.callback-url=", "tributario.niubiz.timeout-url="
})
@AutoConfigureMockMvc
@Import(PostgresEmbebidoConfig.class)
class SandboxSinCredencialesTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void backendDisponibleYGerenciaPuedeIniciarSesion() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        var login = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"gerencia\",\"clave\":\"gerencia123\"}"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/auth/interno/me").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permisos", hasItem("DASHBOARD:CONSULTAR")));
    }

    @Test
    void pagoDevuelve503YNoCreaOrdenNiAlteraLaDeuda() throws Exception {
        var sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, "021390");
        Long antes = jdbc.queryForObject("SELECT count(*) FROM operativo.transaccion_pago", Long.class);
        var deudaAntes = jdbc.queryForObject("SELECT sum(monto_emitido-monto_pagado-monto_descontado) "
                + "FROM samnet.obligacion WHERE contribuyente_codigo='021390'", java.math.BigDecimal.class);
        mvc.perform(post("/api/portal/pagos").session(sesion).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("NIUBIZ_NO_CONFIGURADO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operativo.transaccion_pago", Long.class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT sum(monto_emitido-monto_pagado-monto_descontado) "
                + "FROM samnet.obligacion WHERE contribuyente_codigo='021390'", java.math.BigDecimal.class))
                .isEqualByComparingTo(deudaAntes);
    }
}
