package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU015: firmar digitalmente la solicitud de Constancia de No Adeudo. Se usa el firmador simulado: un
 * certificado con la marca VENCIDO, REVOCADO o NO_ACREDITADO se rechaza. Los contribuyentes son los de los datos
 * de prueba (014502, DNI 41028765, y 021390). Cada test se revierte al terminar.
 */
@PruebaIntegracion
@Transactional
class SolicitudConstanciaControllerTest {

    private static final String RUTA = "/api/constancias";
    private static final String CONTRIBUYENTE = "014502";
    private static final String DOCUMENTO_CONTRIBUYENTE = "41028765";
    private static final String OTRO_CONTRIBUYENTE = "021390";
    private static final String FIRMA_BASE64 =
            Base64.getEncoder().encodeToString("firma-pkcs7-de-prueba".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sinSesionNoSePuedeIniciarNiFirmar() throws Exception {
        mockMvc.perform(post(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
        mockMvc.perform(post(RUTA + "/1/firma").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFirma(FIRMA_BASE64, "CERTIFICADO DNIe DE PRUEBA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void iniciaLaSolicitudEnLaAppPendienteDeFirma() throws Exception {
        mockMvc.perform(post(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(RUTA + "/")))
                .andExpect(jsonPath("$.canal").value("APP"))
                .andExpect(jsonPath("$.estado").value("INICIADA"))
                .andExpect(jsonPath("$.fechaSolicitud").value("2026-10-19T12:00:00"))
                .andExpect(jsonPath("$.firmaContribuyente").value(nullValue()));
    }

    @Test
    void noSeAbreOtraSolicitudMientrasHayaUnaEnCurso() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);

        mockMvc.perform(post(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_EN_CURSO"))
                .andExpect(jsonPath("$.mensaje", containsString(String.valueOf(id))));
    }

    @Test
    void elDocumentoAFirmarLoGeneraElServidorConLaMarcaDePrueba() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);

        String respuesta = mockMvc.perform(get(RUTA + "/" + id + "/documento-firma").session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud").value(id))
                .andExpect(jsonPath("$.nombreArchivo").value("solicitud-constancia-" + id + ".txt"))
                .andExpect(jsonPath("$.documentoDePrueba").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String contenidoBase64 = JsonPath.read(respuesta, "$.contenidoBase64");
        String hash = JsonPath.read(respuesta, "$.hashSha256");
        String documento = new String(Base64.getDecoder().decode(contenidoBase64), StandardCharsets.UTF_8);
        assertThat(documento)
                .startsWith("DOCUMENTO DE PRUEBA")
                .contains("SOLICITUD DE CONSTANCIA DE NO ADEUDO", "Solicitud N.°: " + id, DOCUMENTO_CONTRIBUYENTE);
        assertThat(hash).hasSize(64);
    }

    @Test
    void conFirmaValidaLaSolicitudQuedaFirmadaYSeGuardaLaFirma() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);
        String hashDocumento = JsonPath.read(mockMvc.perform(get(RUTA + "/" + id + "/documento-firma")
                .session(sesion(CONTRIBUYENTE))).andReturn().getResponse().getContentAsString(), "$.hashSha256");

        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO DNIe DE PRUEBA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADA"))
                .andExpect(jsonPath("$.firmaContribuyente.hashDocumento").value(hashDocumento))
                .andExpect(jsonPath("$.firmaContribuyente.entidadCertificadora", containsString("DOCUMENTO DE PRUEBA")))
                .andExpect(jsonPath("$.firmaContribuyente.fechaFirma").value("2026-10-19T12:00:00"));

        mockMvc.perform(get(RUTA + "/" + id).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.estado").value("FIRMADA"));
        Integer firmas = jdbc.queryForObject("""
                SELECT count(*) FROM operativo.firma_digital
                WHERE id_solicitud = ? AND tipo_firmante = 'CONTRIBUYENTE' AND valida
                """, Integer.class, id);
        assertThat(firmas).isEqualTo(1);
    }

    @Test
    void conFirmaNoValidaElTramiteNoAvanzaYSeIndicaElMotivo() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);

        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO VENCIDO DE PRUEBA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"))
                .andExpect(jsonPath("$.mensaje", containsString("vencido")));
        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO NO_ACREDITADO"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("IOFE")));
        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO REVOCADO"))
                .andExpect(jsonPath("$.mensaje", containsString("revocado")));

        mockMvc.perform(get(RUTA + "/" + id).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.estado").value("INICIADA"))
                .andExpect(jsonPath("$.firmaContribuyente").value(nullValue()));
    }

    @Test
    void unaSolicitudYaFirmadaNoSeVuelveAFirmar() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);
        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO DNIe DE PRUEBA"));

        firmar(CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO DNIe DE PRUEBA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FIRMA_NO_PENDIENTE"));
        mockMvc.perform(get(RUTA + "/" + id + "/documento-firma").session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void firmaMalCodificadaOIncompletaDevuelve400() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);

        firmar(CONTRIBUYENTE, id, cuerpoFirma("%%no-es-base64%%", "CERTIFICADO DNIe DE PRUEBA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FIRMA_MAL_CODIFICADA"));
        firmar(CONTRIBUYENTE, id, "{\"firmaBase64\": \"" + FIRMA_BASE64 + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value("certificado: es obligatorio"));
    }

    @Test
    void otroContribuyenteNoVeNiFirmaLaSolicitud() throws Exception {
        long id = iniciarSolicitud(CONTRIBUYENTE);

        mockMvc.perform(get(RUTA + "/" + id).session(sesion(OTRO_CONTRIBUYENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_ENCONTRADA"));
        firmar(OTRO_CONTRIBUYENTE, id, cuerpoFirma(FIRMA_BASE64, "CERTIFICADO DNIe DE PRUEBA"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(RUTA + "/" + id).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.estado").value("INICIADA"));
    }

    private long iniciarSolicitud(String codigoContribuyente) throws Exception {
        String respuesta = mockMvc.perform(post(RUTA).session(sesion(codigoContribuyente)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.idSolicitud")).longValue();
    }

    private ResultActions firmar(String codigoContribuyente, long id, String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA + "/" + id + "/firma").session(sesion(codigoContribuyente))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static MockHttpSession sesion(String codigoContribuyente) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigoContribuyente);
        return sesion;
    }

    private static String cuerpoFirma(String firmaBase64, String certificado) {
        return "{\"firmaBase64\": \"" + firmaBase64 + "\", \"certificado\": \"" + certificado + "\"}";
    }
}
