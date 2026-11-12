package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU024 (RF-037): al confirmarse el pago del derecho de trámite, la constancia queda firmada por la Municipalidad y en
 * el buzón en la misma operación; sin pago confirmado no se genera ni se firma. Se usan la pasarela y el firmador
 * simulados y el contribuyente de prueba 014502 (DNI 41028765, sin deuda).
 */
@PruebaIntegracion
@Transactional
class FirmaAutomaticaConstanciaTest {

    private static final String RUTA_CONTRIBUYENTE = "/api/constancias";
    private static final String RUTA_OPERADOR = "/api/gestion/constancias";
    private static final String SIN_DEUDA = "014502";
    private static final String DNI_SIN_DEUDA = "41028765";
    private static final Duration PLAZO_MAXIMO = Duration.ofMinutes(1);
    private static final String FIRMA_BASE64 =
            Base64.getEncoder().encodeToString("firma-pkcs7-de-prueba".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void trasElPagoLaConstanciaQuedaFirmadaYDescargableDeInmediato() throws Exception {
        long id = conOrdenDePagoIniciada();
        long inicio = System.nanoTime();

        String confirmada = mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion")
                        .session(sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoSolicitud").value("EMITIDA"))
                .andExpect(jsonPath("$.numeroConstancia").value(startsWith("CNA-")))
                .andExpect(jsonPath("$.urlDescarga").value(startsWith("/buzon/")))
                .andReturn().getResponse().getContentAsString();
        String numero = JsonPath.read(confirmada, "$.numeroConstancia");
        String urlDescarga = JsonPath.read(confirmada, "$.urlDescarga");
        /* La firma ocurre dentro de la propia confirmación, así que el plazo se mide en tiempo real. */
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(PLAZO_MAXIMO);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.firma_digital
                WHERE id_solicitud = ? AND tipo_firmante = 'MUNICIPALIDAD' AND valida
                """, Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id_trabajador_emisor FROM operativo.constancia "
                + "WHERE numero_constancia = ?", Long.class, numero)).isNull();

        byte[] pdf = mockMvc.perform(get("/api" + urlDescarga).session(sesion()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        /* Repetir la confirmación no emite otra constancia y devuelve la misma. */
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion").session(sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroConstancia").value(numero))
                .andExpect(jsonPath("$.urlDescarga").value(urlDescarga));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operativo.constancia WHERE id_solicitud = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void sinPagoConfirmadoNoSeGeneraNiSeFirma() throws Exception {
        long id = solicitudConOrdenDePago();

        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion").session(sesion()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_INICIADO"));

        mockMvc.perform(get(RUTA_CONTRIBUYENTE + "/" + id).session(sesion()))
                .andExpect(jsonPath("$.estado").value("ORDEN_PAGO_EMITIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operativo.constancia WHERE id_solicitud = ?",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.firma_digital
                WHERE id_solicitud = ? AND tipo_firmante = 'MUNICIPALIDAD'
                """, Integer.class, id)).isZero();
    }

    @Test
    void siLaFirmaAutomaticaNoPuedeCompletarseElPagoQuedaYElOperadorEmite() throws Exception {
        long id = conOrdenDePagoIniciada();
        jdbc.update("""
                UPDATE operativo.version_plantilla SET vigente = FALSE
                WHERE id_plantilla = (SELECT id_plantilla FROM operativo.plantilla_documento
                                      WHERE codigo = 'CONSTANCIA_NO_ADEUDO')
                """);

        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion").session(sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoSolicitud").value("PAGADA"))
                .andExpect(jsonPath("$.numeroConstancia").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT estado FROM operativo.transaccion_pago "
                + "WHERE id_solicitud_constancia = ?", String.class, id)).isEqualTo("CONFIRMADA");

        jdbc.update("""
                UPDATE operativo.version_plantilla SET vigente = TRUE
                WHERE id_version = (SELECT max(v.id_version) FROM operativo.version_plantilla v
                                    JOIN operativo.plantilla_documento p ON p.id_plantilla = v.id_plantilla
                                    WHERE p.codigo = 'CONSTANCIA_NO_ADEUDO')
                """);
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/emision").with(operador()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.numeroConstancia").value(startsWith("CNA-")));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.documento_buzon b
                JOIN operativo.constancia c ON c.numero_constancia = b.numero_constancia
                WHERE c.id_solicitud = ? AND b.documento_contribuyente = ?
                """, Integer.class, id, DNI_SIN_DEUDA)).isEqualTo(1);
    }

    private long conOrdenDePagoIniciada() throws Exception {
        long id = solicitudConOrdenDePago();
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/sesion").session(sesion()))
                .andExpect(status().isOk());
        return id;
    }

    private long solicitudConOrdenDePago() throws Exception {
        String iniciada = mockMvc.perform(post(RUTA_CONTRIBUYENTE).session(sesion()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(iniciada, "$.idSolicitud")).longValue();
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/firma").session(sesion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmaBase64\": \"" + FIRMA_BASE64 + "\", \"certificado\": \"CERTIFICADO DE PRUEBA\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ORDEN_PAGO_EMITIDA"));
        return id;
    }

    private static RequestPostProcessor operador() {
        return user("analista").authorities(new SimpleGrantedAuthority("CONSTANCIAS:GESTIONAR"));
    }

    private static MockHttpSession sesion() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, SIN_DEUDA);
        return sesion;
    }
}
