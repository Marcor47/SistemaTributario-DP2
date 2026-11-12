package pe.municipalidad.tributario.tramitepredio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU043 (RF-056): Ventanilla abre una solicitud de registro o traspaso de predio enviada desde el portal (HU046), ve
 * sus adjuntos y la deuda del predio y la acepta (registro o traspaso en SAMNET) o la rechaza con motivo; el resultado
 * llega al buzón y queda registrado el revisor. Contribuyentes de prueba: 014502 (DNI 41028765, sin deuda) y 021390
 * (moroso, sus predios tienen deuda).
 */
@PruebaIntegracion
@TestPropertySource(properties = {
        "tributario.archivos.clave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "tributario.archivos.predios=" + RevisionSolicitudPredioControllerTest.CARPETA_ARCHIVOS
})
@Transactional
class RevisionSolicitudPredioControllerTest {

    static final String CARPETA_ARCHIVOS = "./storage/predios/test-hu043";

    private static final String RUTA_PORTAL = "/api/portal/solicitudes-predio";
    private static final String RUTA_REVISION = "/api/gestion/solicitudes-predio";
    private static final String SOLICITANTE = "014502";
    private static final String DNI_SOLICITANTE = "41028765";
    private static final String MOROSO = "021390";
    private static final byte[] PDF_TITULO = "%PDF-1.7 titulo de propiedad".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SamnetGateway samnet;

    @AfterEach
    void limpiarArchivos() throws Exception {
        Path carpeta = Path.of(CARPETA_ARCHIVOS);
        if (Files.exists(carpeta)) {
            try (var rutas = Files.walk(carpeta)) {
                for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(ruta);
                }
            }
        }
    }

    @Test
    void ventanillaVeLaSolicitudConSusAdjuntosYElTipoDeTramiteYPredio() throws Exception {
        long id = enviarRegistro();

        mockMvc.perform(get(RUTA_REVISION).with(ventanilla()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idSolicitud", hasItem((int) id)));
        String detalle = mockMvc.perform(get(RUTA_REVISION + "/" + id).with(ventanilla()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.tipoTramite").value("REGISTRO"))
                .andExpect(jsonPath("$.tipoPredio").value("URBANO"))
                .andExpect(jsonPath("$.contribuyente.codigoContribuyente").value(SOLICITANTE))
                .andExpect(jsonPath("$.adjuntos.length()").value(2))
                .andExpect(jsonPath("$.adjuntos[0].requisito").isString())
                .andExpect(jsonPath("$.adjuntos[0].urlDescarga", containsString("/adjuntos/")))
                .andReturn().getResponse().getContentAsString();

        int idAdjunto = JsonPath.read(detalle, "$.adjuntos[0].id");
        byte[] contenido = mockMvc.perform(get(RUTA_REVISION + "/" + id + "/adjuntos/" + idAdjunto)
                        .with(ventanilla()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(contenido).isEqualTo(PDF_TITULO);
    }

    @Test
    void aceptarUnRegistroCreaElPredioEnSamnetYAvisaAlBuzon() throws Exception {
        long id = enviarRegistro();

        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/aceptacion").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_PREDIO_REQUERIDOS"));

        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/aceptacion").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(datosPredio("HU043-R01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADA"))
                .andExpect(jsonPath("$.codigoPredio").value("HU043-R01"))
                .andExpect(jsonPath("$.revisor").value("ventanilla"))
                .andExpect(jsonPath("$.fechaRevision").isString())
                .andExpect(jsonPath("$.deudaPredio").value(0))
                .andExpect(jsonPath("$.predio.titulares[0].codigoContribuyente").value(SOLICITANTE));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.predio_titular "
                + "WHERE codigo_predio = 'HU043-R01' AND contribuyente_codigo = ?", Integer.class, SOLICITANTE))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT u.nombre_usuario FROM operativo.solicitud_predio s "
                + "JOIN seguridad.usuario u ON u.id_usuario = s.id_revisor WHERE s.id_solicitud = ?",
                String.class, id)).isEqualTo("ventanilla");
        mockMvc.perform(get("/api/buzon").session(sesion(SOLICITANTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..titulo", hasItem("Solicitud de registro de predio N.° " + id + ": aceptada")));

        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/rechazo").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Tarde\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_EN_REVISION"));
        mockMvc.perform(get(RUTA_REVISION).with(ventanilla()))
                .andExpect(jsonPath("$[*].idSolicitud", not(hasItem((int) id))));
    }

    @Test
    void aceptarUnTraspasoCambiaElTitularYNotificaLaDeudaDelPredio() throws Exception {
        String predio = predioConDeuda();
        long id = enviarTraspaso(predio);

        String detalle = mockMvc.perform(get(RUTA_REVISION + "/" + id).with(ventanilla()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.titulares[*].codigoContribuyente", hasItem(MOROSO)))
                .andExpect(jsonPath("$.deudaPredio", greaterThan(0.0)))
                .andExpect(jsonPath("$.obligacionesPendientes[0].codigoPredio").value(predio))
                .andReturn().getResponse().getContentAsString();
        BigDecimal deuda = new BigDecimal(JsonPath.read(detalle, "$.deudaPredio").toString());

        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/aceptacion").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADA"))
                .andExpect(jsonPath("$.predio.titulares.length()").value(1))
                .andExpect(jsonPath("$.predio.titulares[0].codigoContribuyente").value(SOLICITANTE));

        assertThat(jdbc.queryForObject("SELECT deuda_predio FROM operativo.solicitud_predio WHERE id_solicitud = ?",
                BigDecimal.class, id)).isEqualByComparingTo(deuda);
        assertThat(jdbc.queryForList("SELECT contribuyente_codigo FROM samnet.predio_titular WHERE codigo_predio = ?",
                String.class, predio)).containsExactly(SOLICITANTE);
        String contenido = jdbc.queryForObject("SELECT titulo FROM operativo.documento_buzon "
                + "WHERE id_solicitud_predio = ? AND documento_contribuyente = ?", String.class, id, DNI_SOLICITANTE);
        assertThat(contenido).endsWith("aceptada");
        mockMvc.perform(get("/api/buzon").session(sesion(SOLICITANTE)))
                .andExpect(jsonPath("$..contenido", hasItem(containsString(
                        "Deuda existente del predio: S/ " + deuda.setScale(2).toPlainString()))));
    }

    @Test
    void rechazarExigeMotivoYEnviaAlBuzonElMotivoYLaDeuda() throws Exception {
        String predio = predioConDeuda();
        long id = enviarTraspaso(predio);

        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/rechazo").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(RUTA_REVISION + "/" + id + "/rechazo").with(ventanilla()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"La minuta no está firmada por el vendedor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("La minuta no está firmada por el vendedor"))
                .andExpect(jsonPath("$.revisor").value("ventanilla"))
                .andExpect(jsonPath("$.deudaPredio", greaterThan(0.0)));

        assertThat(jdbc.queryForList("SELECT contribuyente_codigo FROM samnet.predio_titular WHERE codigo_predio = ?",
                String.class, predio)).contains(MOROSO).doesNotContain(SOLICITANTE);
        mockMvc.perform(get("/api/buzon").session(sesion(SOLICITANTE)))
                .andExpect(jsonPath("$..contenido", hasItem(allOf(
                        containsString("Motivo: La minuta no está firmada por el vendedor"),
                        containsString("Deuda existente del predio: S/ ")))));
        mockMvc.perform(get(RUTA_REVISION).param("estado", "RECHAZADA").with(ventanilla()))
                .andExpect(jsonPath("$[*].idSolicitud", hasItem((int) id)));
    }

    @Test
    void sinPermisoDeRevisionNoSeAccedeALaBandeja() throws Exception {
        long id = enviarRegistro();

        mockMvc.perform(get(RUTA_REVISION))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_REVISION).with(user("ventanilla")
                        .authorities(new SimpleGrantedAuthority("CONSTANCIAS:GESTIONAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA_REVISION + "/" + id).session(sesion(SOLICITANTE)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_REVISION + "/999999").with(ventanilla()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_ENCONTRADA"));
    }

    private long enviarRegistro() throws Exception {
        List<Long> requisitos = requisitosObligatorios("REGISTRO");
        return enviar(multipart(RUTA_PORTAL)
                .file(new MockMultipartFile("archivos", "titulo.pdf", "application/pdf", PDF_TITULO))
                .file(new MockMultipartFile("archivos", "autoavaluo.pdf", "application/pdf",
                        "%PDF-1.7 autoavaluo".getBytes()))
                .param("tipoTramite", "REGISTRO").param("tipoPredio", "URBANO")
                .param("requisitoIds", requisitos.get(0).toString(), requisitos.get(1).toString()));
    }

    private long enviarTraspaso(String codigoPredio) throws Exception {
        List<Long> requisitos = requisitosObligatorios("TRASPASO");
        return enviar(multipart(RUTA_PORTAL)
                .file(new MockMultipartFile("archivos", "minuta.pdf", "application/pdf", PDF_TITULO))
                .file(new MockMultipartFile("archivos", "dni.pdf", "application/pdf", "%PDF-1.7 dni".getBytes()))
                .param("tipoTramite", "TRASPASO").param("tipoPredio", "URBANO").param("codigoPredio", codigoPredio)
                .param("requisitoIds", requisitos.get(0).toString(), requisitos.get(1).toString()));
    }

    private long enviar(MockMultipartHttpServletRequestBuilder solicitud) throws Exception {
        String creada = mockMvc.perform(solicitud.session(sesion(SOLICITANTE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(creada, "$.idSolicitud")).longValue();
    }

    private List<Long> requisitosObligatorios(String tramite) {
        return jdbc.queryForList("SELECT id_requisito FROM operativo.requisito_documental "
                + "WHERE tipo_tramite = ? AND tipo_predio = 'URBANO' AND obligatorio ORDER BY id_requisito",
                Long.class, tramite);
    }

    /* Un predio urbano del contribuyente moroso con cuotas pendientes en SAMNET. */
    private String predioConDeuda() {
        return samnet.obtenerEstadoCuenta(MOROSO).getObligaciones().stream()
                .filter(o -> o.saldo().signum() > 0 && o.getCodigoPredio() != null)
                .map(Obligacion::getCodigoPredio)
                .filter(codigo -> samnet.buscarPredio(codigo, null).stream()
                        .anyMatch(p -> p.getTipoPredio().name().equals("URBANO")))
                .findFirst().orElseThrow();
    }

    private static String datosPredio(String codigoPredio) {
        return """
                {"predio": {"codigoPredio": "%s", "numeroCatastral": "150136-099-001",
                 "ubicacion": "Jr. Prueba 123, San Miguel", "usoPredio": "Vivienda", "areaTerreno": 120.50,
                 "baseImponible": 150000, "fechaAdquisicion": "2025-03-10", "zona": "Maranga"}}
                """.formatted(codigoPredio);
    }

    private static RequestPostProcessor ventanilla() {
        return user("ventanilla").authorities(new SimpleGrantedAuthority("SOLICITUDES_PREDIO:REVISAR"));
    }

    private static MockHttpSession sesion(String codigoContribuyente) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigoContribuyente);
        return sesion;
    }
}
