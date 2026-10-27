package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@TestPropertySource(properties = {
        "tributario.archivos.clave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "tributario.archivos.predios=./storage/predios/test"
})
@Transactional
class PortalContribuyenteTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpiarArchivos() throws Exception {
        Path carpeta = Path.of("storage/predios/test");
        if (Files.exists(carpeta)) {
            try (var rutas = Files.walk(carpeta)) {
                for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(ruta);
            }
        }
    }

    @Test
    void loginConPostgresYCuentaPropia() throws Exception {
        jdbc.update("INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash) "
                + "VALUES ('021390', public.crypt('clave-segura', public.gen_salt('bf', 4)))");
        mvc.perform(get("/api/portal/estado-cuenta")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"20456781239\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"20456781239\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("021390"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/portal/estado-cuenta").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("021390"));
    }

    @Test
    void pagoDePruebaSeRegistraUnaVezYActualizaLaCuenta() throws Exception {
        var creado = mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INICIADA"))
                .andExpect(jsonPath("$.dePrueba").value(true)).andReturn();
        int transaccion = JsonPath.read(creado.getResponse().getContentAsString(), "$.idTransaccion");
        String ruta = "/api/portal/pagos/" + transaccion + "/confirmacion";
        mvc.perform(post(ruta).session(sesion("014502"))).andExpect(status().isNotFound());
        mvc.perform(post(ruta).session(sesion("021390")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(post(ruta).session(sesion("021390"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.obligacion WHERE contribuyente_codigo = '021390' "
                + "AND monto_emitido > monto_pagado + monto_descontado", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago = ?", Integer.class,
                "WEB-" + transaccion)).isEqualTo(1);
    }

    @Test
    void segundaOrdenSobreLaMismaDeudaNoSeCobraDosVeces() throws Exception {
        String cuerpo = "{}";
        int primera = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        int segunda = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        mvc.perform(post("/api/portal/pagos/" + primera + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/portal/pagos/" + segunda + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_CAMBIO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago IN (?, ?)",
                Integer.class, "WEB-" + primera, "WEB-" + segunda)).isEqualTo(1);
    }

    @Test
    void pagoSoloAceptaObligacionesPropiasYVariasSeAplicanCompletas() throws Exception {
        Long ajena = jdbc.queryForObject("SELECT id FROM samnet.obligacion WHERE contribuyente_codigo = '058934' "
                + "AND monto_pagado = 0 ORDER BY id LIMIT 1", Long.class);
        mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"obligaciones\":[" + ajena + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAGO_DEBE_CUBRIR_TODO"));
        Long pagada = jdbc.queryForObject("SELECT id FROM samnet.obligacion WHERE contribuyente_codigo='021390' "
                + "AND monto_pagado=monto_emitido ORDER BY id LIMIT 1", Long.class);
        mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"obligaciones\":[" + pagada + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAGO_DEBE_CUBRIR_TODO"));
        var propias = jdbc.queryForList("SELECT id FROM samnet.obligacion "
                + "WHERE contribuyente_codigo = '021390' AND monto_pagado = 0 ORDER BY id LIMIT 2", Long.class);
        String cuerpo = "{\"obligaciones\":[" + propias.get(0) + "," + propias.get(1) + "]}";
        mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAGO_DEBE_CUBRIR_TODO"));
        var todas = jdbc.queryForList("SELECT id FROM samnet.obligacion WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado ORDER BY id", Long.class);
        String listaCompleta = "{\"obligaciones\":[" + todas.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
        int transaccion = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content(listaCompleta))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        mvc.perform(post("/api/portal/pagos/" + transaccion + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago_obligacion WHERE id_pago = ?",
                Integer.class, "WEB-" + transaccion)).isGreaterThan(2);
    }

    @Test
    void sinDeudaNoCreaOrdenYBeneficioIndeterminadoDerivaAVentanilla() throws Exception {
        mvc.perform(post("/api/portal/pagos").session(sesion("033218"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SIN_DEUDA"));
        mvc.perform(post("/api/portal/pagos").session(sesion("058934"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("BENEFICIO_INDETERMINADO"));
    }

    @Test
    void beneficioVigenteSeDesglosaSinConfundirDescuentoConDinero() throws Exception {
        jdbc.update("INSERT INTO samnet.beneficio_tributario VALUES "
                + "('BEN-HU022-TEST', '021390', 'PARTICULAR', 50, DATE '2026-01-01', DATE '2026-12-31')");
        var ids = jdbc.queryForList("SELECT id FROM samnet.obligacion WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado ORDER BY id LIMIT 2", Long.class);
        jdbc.update("UPDATE samnet.obligacion SET porcentaje_descuento=50, id_beneficio='BEN-HU022-TEST' "
                + "WHERE id IN (?, ?)", ids.get(0), ids.get(1));
        var creado = mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoDescuento").isNumber()).andReturn();
        int transaccion = JsonPath.read(creado.getResponse().getContentAsString(), "$.idTransaccion");
        var montos = jdbc.queryForMap("SELECT monto_base, monto_descuento, monto_total FROM operativo.transaccion_pago "
                + "WHERE id_transaccion=?", transaccion);
        assertThat((BigDecimal) montos.get("monto_descuento")).isPositive();
        assertThat((BigDecimal) montos.get("monto_base")).isEqualByComparingTo(
                ((BigDecimal) montos.get("monto_total")).add((BigDecimal) montos.get("monto_descuento")));
        mvc.perform(post("/api/portal/pagos/" + transaccion + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isOk());
        var pago = jdbc.queryForMap("SELECT monto_pagado, monto_correspondiente FROM samnet.pago "
                + "WHERE id_pago=?", "WEB-" + transaccion);
        assertThat((BigDecimal) pago.get("monto_pagado")).isEqualByComparingTo((BigDecimal) montos.get("monto_total"));
        assertThat((BigDecimal) pago.get("monto_correspondiente"))
                .isEqualByComparingTo((BigDecimal) montos.get("monto_base"));
        assertThat(jdbc.queryForObject("SELECT sum(monto_descuento) FROM samnet.pago_obligacion WHERE id_pago=?",
                BigDecimal.class, "WEB-" + transaccion)).isEqualByComparingTo((BigDecimal) montos.get("monto_descuento"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.obligacion WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado + monto_descontado", Integer.class)).isZero();
    }

    @Test
    void beneficioVencidoNoSeAplicaYElCienPorCientoNoCreaCobro() throws Exception {
        jdbc.update("INSERT INTO samnet.beneficio_tributario VALUES "
                + "('BEN-VENCIDO', '021390', 'AMNISTIA', 100, DATE '2025-01-01', DATE '2025-12-31')");
        mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montoDescuento").value(0));
        jdbc.update("INSERT INTO samnet.beneficio_tributario VALUES "
                + "('BEN-TOTAL', '021390', 'AMNISTIA', 100, DATE '2026-01-01', DATE '2026-12-31')");
        jdbc.update("UPDATE samnet.obligacion SET porcentaje_descuento=100, id_beneficio='BEN-TOTAL' "
                + "WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado");
        mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SIN_COBRO_VIRTUAL"));
    }

    @Test
    void nuevaDeudaOBeneficioCambiadoInvalidaLaOrden() throws Exception {
        int primera = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        jdbc.update("UPDATE samnet.obligacion SET monto_pagado=monto_pagado-1 WHERE id="
                + "(SELECT id FROM samnet.obligacion WHERE contribuyente_codigo='021390' AND monto_pagado=monto_emitido "
                + "ORDER BY id LIMIT 1)");
        mvc.perform(post("/api/portal/pagos/" + primera + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_CAMBIO"));
        int segunda = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        jdbc.update("INSERT INTO samnet.beneficio_tributario VALUES "
                + "('BEN-CAMBIO', '021390', 'PARTICULAR', 10, DATE '2026-01-01', DATE '2026-12-31')");
        jdbc.update("UPDATE samnet.obligacion SET porcentaje_descuento=10, id_beneficio='BEN-CAMBIO' "
                + "WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado");
        mvc.perform(post("/api/portal/pagos/" + segunda + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_CAMBIO"));
    }

    @Test
    void beneficioIndeterminableAntesDeConfirmarBloqueaYDerivaAVentanilla() throws Exception {
        int transaccion = JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idTransaccion");
        jdbc.update("UPDATE samnet.obligacion SET porcentaje_descuento=10 WHERE contribuyente_codigo='021390' "
                + "AND monto_emitido > monto_pagado");
        mvc.perform(post("/api/portal/pagos/" + transaccion + "/confirmacion").session(sesion("021390")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("BENEFICIO_INDETERMINADO"));
    }

    @Test
    void solicitudValidaDocumentosYNoExponeLosDeOtroContribuyente() throws Exception {
        var requisitoIds = jdbc.queryForList("SELECT id_requisito FROM operativo.requisito_documental "
                + "WHERE tipo_tramite='REGISTRO' AND tipo_predio='URBANO' AND obligatorio "
                + "ORDER BY id_requisito", Long.class);
        assertThat(requisitoIds).hasSize(2);
        MockMultipartFile primero = new MockMultipartFile("archivos", "titulo.pdf", "application/pdf",
                "%PDF-1.7 prueba".getBytes());
        MockMultipartFile segundo = new MockMultipartFile("archivos", "declaracion.pdf", "application/pdf",
                "%PDF-1.7 declaracion".getBytes());
        var creado = mvc.perform(multipart("/api/portal/solicitudes-predio").file(primero).file(segundo)
                .param("tipoTramite", "REGISTRO").param("tipoPredio", "URBANO")
                .param("requisitoIds", requisitoIds.get(0).toString(), requisitoIds.get(1).toString())
                .session(sesion("014502")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.adjuntos.length()").value(2)).andReturn();
        int solicitud = JsonPath.read(creado.getResponse().getContentAsString(), "$.idSolicitud");
        int adjunto = JsonPath.read(creado.getResponse().getContentAsString(), "$.adjuntos[0].id");
        mvc.perform(get("/api/portal/solicitudes-predio/" + solicitud).session(sesion("021390")))
                .andExpect(status().isNotFound());
        var descarga = mvc.perform(get("/api/portal/solicitudes-predio/" + solicitud + "/adjuntos/" + adjunto)
                .session(sesion("014502")))
                .andExpect(status().isOk()).andReturn();
        assertThat(descarga.getResponse().getContentAsByteArray()).isEqualTo(primero.getBytes());
        String ruta = jdbc.queryForObject("SELECT ruta_cifrada FROM operativo.documento_adjunto "
                + "WHERE id_adjunto = ?", String.class, adjunto);
        assertThat(new String(Files.readAllBytes(Path.of(ruta)), StandardCharsets.ISO_8859_1))
                .doesNotContain("%PDF-1.7 prueba");
    }

    @Test
    void solicitudRechazaArchivoDisfrazadoYRequisitosIncompletos() throws Exception {
        var requisitoIds = jdbc.queryForList("SELECT id_requisito FROM operativo.requisito_documental "
                + "WHERE tipo_tramite='REGISTRO' AND tipo_predio='URBANO' AND obligatorio "
                + "ORDER BY id_requisito", Long.class);
        MockMultipartFile falso = new MockMultipartFile("archivos", "falso.pdf", "application/pdf",
                "contenido no PDF".getBytes());
        mvc.perform(multipart("/api/portal/solicitudes-predio").file(falso)
                .param("tipoTramite", "REGISTRO").param("tipoPredio", "URBANO")
                .param("requisitoIds", requisitoIds.get(0).toString()).session(sesion("014502")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TIPO_ARCHIVO_INVALIDO"));
        MockMultipartFile valido = new MockMultipartFile("archivos", "titulo.pdf", "application/pdf",
                "%PDF-1.7 prueba".getBytes());
        mvc.perform(multipart("/api/portal/solicitudes-predio").file(valido)
                .param("tipoTramite", "REGISTRO").param("tipoPredio", "URBANO")
                .param("requisitoIds", requisitoIds.get(0).toString()).session(sesion("014502")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REQUISITOS_FALTANTES"));
    }

    private MockHttpSession sesion(String codigo) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigo);
        return sesion;
    }
}
