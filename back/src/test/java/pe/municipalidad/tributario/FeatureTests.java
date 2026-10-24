package pe.municipalidad.tributario;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.auth.AuthController;
import pe.municipalidad.tributario.auth.DemoMunicipalAuthClient;
import pe.municipalidad.tributario.auth.LoginLimiter;
import pe.municipalidad.tributario.common.DemoInternalAccess;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.formularios.FormularioService;
import pe.municipalidad.tributario.predios.PredioGateway;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Import(PostgresEmbebidoConfig.class)
@TestPropertySource(properties = {
        "tributario.demo.internal-read-key=lectura-test",
        "tributario.demo.internal-admin-key=admin-test",
        "tributario.demo.citizen-code=CIUDADANO",
        "tributario.demo.citizen-password=secreto-de-prueba"})
class FeatureTests {
    @Autowired MockMvc mvc;
    @Autowired PredioGateway predios;
    @Autowired FormularioService formularios;
    @Autowired VersionPlantillaRepository repository;

    @Test
    void buscaPredioPorCodigoOTitularYDevuelveListaVaciaSiNoExiste() {
        assertEquals("DEM-001", predios.buscar("dem-001").getFirst().codPredio());
        assertEquals("DEM-001", predios.buscar("contribuyente de prueba").getFirst().codPredio());
        assertEquals(2, predios.buscar("SOCIO DE PRUEBA").getFirst().contribuyentes().size());
        assertTrue(predios.buscar("inexistente").isEmpty());
    }

    @Test
    void loginNoDevuelvePasswordYBloqueaTrasCincoFallos() {
        AuthController auth = new AuthController(new DemoMunicipalAuthClient("CIUDADANO", "secreto-de-prueba"),
                new LoginLimiter());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.42");
        assertEquals("CIUDADANO", auth.login(new AuthController.LoginRequest("CIUDADANO", "secreto-de-prueba"),
                request).codigoContribuyente());
        assertEquals("CIUDADANO", auth.me(request).codigoContribuyente());
        assertEquals(600, request.getSession(false).getMaxInactiveInterval());
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
    void publicaNuevaVersionYConservaHistorial() {
        byte[] primera = "%PDF-1.4\nprimera".getBytes(StandardCharsets.US_ASCII);
        byte[] segunda = "%PDF-1.4\nsegunda".getBytes(StandardCharsets.US_ASCII);
        assertEquals(1, formularios.publicar("FORM_TEST", "Formulario de prueba", "original.pdf", primera).version());
        assertEquals(2, formularios.publicar("FORM_TEST", "Formulario de prueba", "original.pdf", segunda).version());
        assertEquals(2, formularios.vigente("FORM_TEST").getNumeroVersion());
        assertArrayEquals(segunda, formularios.vigente("FORM_TEST").descargar());
        assertEquals(2, repository.findByPlantillaCodigo("FORM_TEST").size());
        assertEquals(1, repository.findByPlantillaCodigo("FORM_TEST").stream().filter(f -> f.isVigente()).count());
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
                .param("nombre", "Formulario HTTP").header("X-Demo-Internal-Key", "lectura-test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/formularios").file(archivo).param("codigo", "FORM_HTTP")
                .param("nombre", "Formulario HTTP").header("X-Demo-Internal-Key", "admin-test"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/formularios/FORM_HTTP/descarga")
                .header("X-Demo-Internal-Key", "lectura-test"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void loginHttpCreaSesionPropiaSinDevolverPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"CIUDADANO\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoContribuyente\":\"CIUDADANO\",\"password\":\"secreto-de-prueba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("CIUDADANO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secreto-de-prueba"))))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoContribuyente").value("CIUDADANO"));
    }
}
