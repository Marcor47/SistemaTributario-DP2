package pe.municipalidad.tributario;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.auth.AuthController;
import pe.municipalidad.tributario.auth.LoginLimiter;
import pe.municipalidad.tributario.auth.MunicipalAuthClient;
import pe.municipalidad.tributario.common.DemoInternalAccess;
import pe.municipalidad.tributario.formularios.FormularioRepository;
import pe.municipalidad.tributario.formularios.FormularioService;
import pe.municipalidad.tributario.predios.PredioGateway;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@PruebaIntegracion
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "tributario.local.internal-read-key=lectura-test",
        "tributario.local.internal-admin-key=admin-test"})
class FeatureTests {
    @Autowired MockMvc mvc;
    @Autowired PredioGateway predios;
    @Autowired FormularioService formularios;
    @Autowired FormularioRepository repository;
    @Autowired MunicipalAuthClient autenticacion;
    @Autowired JdbcClient jdbc;

    @Test
    void buscaPredioPorCodigoOTitularYDevuelveListaVaciaSiNoExiste() {
        assertEquals("DEM-001", predios.buscar("dem-001").getFirst().codPredio());
        assertEquals("DEM-001", predios.buscar("contribuyente de prueba").getFirst().codPredio());
        assertEquals(2, predios.buscar("SOCIO DE PRUEBA").getFirst().contribuyentes().size());
        assertTrue(predios.buscar("inexistente").isEmpty());
    }

    @Test
    void loginNoDevuelvePasswordYBloqueaTrasCincoFallos() {
        AuthController auth = new AuthController(autenticacion, new LoginLimiter());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.42");
        assertEquals("014502", auth.login(new AuthController.LoginRequest("014502", "DemoSM#014502"),
                request).codigoContribuyente());
        assertEquals("014502", auth.me(request).codigoContribuyente());
        assertEquals(900, request.getSession(false).getMaxInactiveInterval());
        auth.logout(request);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> auth.me(request)).getStatusCode());

        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                    () -> auth.login(new AuthController.LoginRequest("OTRO", "erronea"), request))
                    .getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> auth.login(new AuthController.LoginRequest("OTRO", "erronea"), request))
                .getStatusCode());
    }

    @Test
    void autenticaLasSieteCuentasConBcryptYRespetaLaHabilitacion() {
        Map<String, String> cuentas = Map.of(
                "014502", "DemoSM#014502",
                "021390", "DemoSM#021390",
                "033218", "DemoSM#033218",
                "045067", "DemoSM#045067",
                "058934", "DemoSM#058934",
                "061177", "DemoSM#061177",
                "072405", "DemoSM#072405");
        cuentas.forEach((codigo, password) -> assertEquals(codigo,
                autenticacion.autenticar(codigo, password).orElseThrow()));
        assertTrue(autenticacion.autenticar("999999", "DemoSM#999999").isEmpty());
        assertTrue(autenticacion.autenticar("014502", "incorrecta").isEmpty());

        assertEquals(7L, jdbc.sql("SELECT COUNT(*) FROM seguridad.credencial_contribuyente_local")
                .query(Long.class).single());
        assertTrue(jdbc.sql("SELECT password_hash FROM seguridad.credencial_contribuyente_local")
                .query(String.class).list().stream().allMatch(hash -> hash.startsWith("$2a$12$")));

        jdbc.sql("UPDATE seguridad.credencial_contribuyente_local SET habilitada = FALSE "
                + "WHERE codigo_contribuyente = '072405'").update();
        try {
            assertTrue(autenticacion.autenticar("072405", "DemoSM#072405").isEmpty());
        } finally {
            jdbc.sql("UPDATE seguridad.credencial_contribuyente_local SET habilitada = TRUE "
                    + "WHERE codigo_contribuyente = '072405'").update();
        }
    }

    @Test
    void publicaNuevaVersionYConservaHistorial() {
        byte[] primera = "%PDF-1.4\nprimera".getBytes(StandardCharsets.US_ASCII);
        byte[] segunda = "%PDF-1.4\nsegunda".getBytes(StandardCharsets.US_ASCII);
        assertEquals(1, formularios.publicar("FORM_TEST", "Formulario de prueba", "original.pdf", primera).version());
        assertEquals(2, formularios.publicar("FORM_TEST", "Formulario de prueba", "original.pdf", segunda).version());
        assertEquals(2, formularios.vigente("FORM_TEST").getVersionNumero());
        assertArrayEquals(segunda, formularios.vigente("FORM_TEST").getContenido());
        assertEquals(2, repository.findAll().stream().filter(f -> f.getCodigo().equals("FORM_TEST")).count());
        assertEquals(1, repository.findAll().stream().filter(f -> f.getCodigo().equals("FORM_TEST") && f.isVigente()).count());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> formularios.publicar("FORM_TEST", "Formulario", "malo.pdf", new byte[] {1, 2, 3}))
                .getStatusCode());
    }

    @Test
    void aceptaDocxYRechazaUnZipRenombrado() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        assertEquals(1, formularios.publicar("DOCX_TEST", "Plantilla de prueba", "plantilla.docx",
                buffer.toByteArray()).version());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> formularios.publicar("ZIP_TEST", "Archivo falso", "archivo.docx",
                        "no es un zip".getBytes(StandardCharsets.UTF_8))).getStatusCode());
    }

    @Test
    void accesoInternoDistingueLecturaYPublicacion() {
        DemoInternalAccess access = new DemoInternalAccess("lectura-test", "admin-test");
        assertDoesNotThrow(() -> access.requireRead("lectura-test"));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> access.requireAdmin("lectura-test")).getStatusCode());
        assertDoesNotThrow(() -> access.requireAdmin("admin-test"));
    }

    @Test
    void contratosHttpRequierenClaveYPermitenConsultaPublicacionYDescarga() throws Exception {
        mvc.perform(get("/api/predios").param("consulta", "DEM-001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/predios").param("consulta", "DEM-001")
                .header("X-Demo-Internal-Key", "lectura-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codPredio").value("DEM-001"));
        mvc.perform(get("/api/predios").param("consulta", "ninguno")
                .header("X-Demo-Internal-Key", "lectura-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        MockMultipartFile archivo = new MockMultipartFile("archivo", "oficial.pdf", "application/pdf",
                "%PDF-1.4\ncontenido".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/formularios").file(archivo).param("codigo", "FORM_HTTP")
                .param("nombre", "Formulario HTTP").header("X-Demo-Internal-Key", "lectura-test").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/formularios").file(archivo).param("codigo", "FORM_HTTP")
                .param("nombre", "Formulario HTTP").header("X-Demo-Internal-Key", "admin-test").with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/formularios/FORM_HTTP/descarga")
                .header("X-Demo-Internal-Key", "lectura-test"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void loginHttpCreaSesionPropiaSinDevolverPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"014502\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"014502\",\"password\":\"DemoSM#014502\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("DemoSM#014502"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("$2a$12$"))))
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoContribuyente").value("014502"));
    }

    @Test
    void loginMovilUsaLaMismaApiConSesionEnCabecera() throws Exception {
        var csrfResult = mvc.perform(get("/api/auth/csrf").header("X-Client-Platform", "mobile"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Auth-Token")).andReturn();
        String tokenSesion = csrfResult.getResponse().getHeader("X-Auth-Token");
        String cuerpoCsrf = csrfResult.getResponse().getContentAsString();
        String tokenCsrf = JsonPath.read(cuerpoCsrf, "$.token");
        String cabeceraCsrf = JsonPath.read(cuerpoCsrf, "$.headerName");

        var login = mvc.perform(post("/api/auth/login").header("X-Client-Platform", "mobile")
                        .header("X-Auth-Token", tokenSesion).header(cabeceraCsrf, tokenCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoContribuyente\":\"021390\",\"password\":\"DemoSM#021390\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Auth-Token")).andReturn();
        String sesionAutenticada = login.getResponse().getHeader("X-Auth-Token");

        mvc.perform(get("/api/auth/me").header("X-Client-Platform", "mobile")
                        .header("X-Auth-Token", sesionAutenticada))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoContribuyente").value("021390"));
    }
}
