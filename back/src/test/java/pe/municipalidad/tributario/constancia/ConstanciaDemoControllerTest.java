package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
@TestPropertySource(properties = {
        "tributario.constancia.demo.habilitado=true",
        "tributario.constancia.demo.derecho=10.00"
})
class ConstanciaDemoControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession sesion(String codigo) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigo);
        return sesion;
    }

    @Test
    void sinDeudaFirmaPagaEmiteYDescargaSoloSuDocumento() throws Exception {
        MockHttpSession propia = sesion("014502");
        String iniciada = mvc.perform(post("/api/constancias").session(propia))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(iniciada, "$.idSolicitud")).longValue();
        String firma = Base64.getEncoder().encodeToString("firma de prueba".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/constancias/" + id + "/firma").session(propia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmaBase64\":\"" + firma + "\",\"certificado\":\"CERTIFICADO DE PRUEBA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FIRMADA"));
        mvc.perform(post("/api/constancias/" + id + "/emitir").session(propia))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("PAGO_REQUERIDO"));
        mvc.perform(post("/api/constancias/" + id + "/orden").session(propia))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monto").value(10.00));
        mvc.perform(post("/api/constancias/" + id + "/pago").session(propia))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        String emitida = mvc.perform(post("/api/constancias/" + id + "/emitir").session(propia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long documento = ((Number) JsonPath.read(emitida, "$.id")).longValue();
        mvc.perform(post("/api/constancias/" + id + "/emitir").session(propia))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(documento));
        mvc.perform(get("/api/constancias/buzon").session(propia))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(documento));
        byte[] pdf = mvc.perform(get("/api/constancias/buzon/" + documento + "/descarga").session(propia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var documentoPdf = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documentoPdf))
                    .contains("DOCUMENTO DE PRUEBA", "GARCÍA TORRES, MARÍA ELENA", "Firma municipal simulada");
        }
        mvc.perform(get("/api/constancias/buzon/" + documento + "/descarga").session(sesion("033218")))
                .andExpect(status().isNotFound());
    }

    @Test
    void noConfirmaPagoSiApareceDeudaDespuesDeGenerarLaOrden() throws Exception {
        MockHttpSession propia = sesion("014502");
        String iniciada = mvc.perform(post("/api/constancias").session(propia))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(iniciada, "$.idSolicitud")).longValue();
        String firma = Base64.getEncoder().encodeToString("firma de prueba".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/constancias/" + id + "/firma").session(propia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmaBase64\":\"" + firma + "\",\"certificado\":\"CERTIFICADO DE PRUEBA\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/constancias/" + id + "/orden").session(propia)).andExpect(status().isOk());
        jdbc.update("UPDATE samnet.obligacion SET monto_pagado = 0 WHERE contribuyente_codigo = ?", "014502");
        mvc.perform(post("/api/constancias/" + id + "/pago").session(propia))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("DEUDA_PENDIENTE"));
        mvc.perform(get("/api/constancias/" + id + "/orden").session(propia))
                .andExpect(jsonPath("$.estado").value("INICIADA"));
    }
}
