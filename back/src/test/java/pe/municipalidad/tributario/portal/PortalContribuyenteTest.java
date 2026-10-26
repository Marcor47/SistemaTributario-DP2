package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
        Long id = jdbc.queryForObject("SELECT id FROM samnet.obligacion WHERE contribuyente_codigo = '021390' "
                + "AND monto_pagado = 0 ORDER BY id LIMIT 1", Long.class);
        var creado = mvc.perform(post("/api/portal/pagos").session(sesion("021390"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"obligaciones\":[" + id + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INICIADA"))
                .andExpect(jsonPath("$.dePrueba").value(true)).andReturn();
        int transaccion = JsonPath.read(creado.getResponse().getContentAsString(), "$.idTransaccion");
        String ruta = "/api/portal/pagos/" + transaccion + "/confirmacion";
        mvc.perform(post(ruta).session(sesion("014502"))).andExpect(status().isNotFound());
        mvc.perform(post(ruta).session(sesion("021390")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(post(ruta).session(sesion("021390"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT monto_emitido = monto_pagado FROM samnet.obligacion WHERE id = ?",
                Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago = ?", Integer.class,
                "WEB-" + transaccion)).isEqualTo(1);
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
        mvc.perform(get("/api/portal/solicitudes-predio/" + solicitud + "/adjuntos/" + adjunto)
                .session(sesion("014502")))
                .andExpect(status().isOk());
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
