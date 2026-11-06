package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
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
 * HU020: la solicitud firmada llega al operador, que verifica la deuda en SAMNET y emite la orden de pago; tras el
 * pago, el operador emite la constancia y la envía al buzón. Se usan la pasarela y el firmador simulados y los
 * contribuyentes de prueba: 014502 (DNI 41028765, sin deuda) y 021390 (RUC 20456781239, con deuda).
 */
@PruebaIntegracion
@Transactional
class ConstanciaOperadorControllerTest {

    private static final String RUTA_CONTRIBUYENTE = "/api/constancias";
    private static final String RUTA_OPERADOR = "/api/gestion/constancias";
    private static final String SIN_DEUDA = "014502";
    private static final String RUC_CON_DEUDA = "20456781239";
    private static final String FIRMA_BASE64 =
            Base64.getEncoder().encodeToString("firma-pkcs7-de-prueba".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flujoCompletoDelOperadorHastaElBuzon() throws Exception {
        long id = solicitudFirmada(SIN_DEUDA);

        mockMvc.perform(get(RUTA_OPERADOR).with(operador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idSolicitud", hasItem((int) id)));
        mockMvc.perform(get(RUTA_OPERADOR + "/" + id).with(operador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADA"))
                .andExpect(jsonPath("$.deuda.puedeSolicitar").value(true))
                .andExpect(jsonPath("$.firmaContribuyente.hashDocumento").value(notNullValue()));

        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ORDEN_PAGO_EMITIDA"))
                .andExpect(jsonPath("$.operador").value("analista"))
                .andExpect(jsonPath("$.ordenPago.monto").value(10.00))
                .andExpect(jsonPath("$.ordenPago.estado").value("INICIADA"));

        MockHttpSession sesion = sesion(SIN_DEUDA);
        mockMvc.perform(get(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(10.00));
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion").session(sesion))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_INICIADO"));
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/sesion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPasarela").value(startsWith("/pasarela-simulada/")));
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/confirmacion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.comprobante").value(startsWith("CONST-")));

        String emitida = mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/emision").with(operador()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.numeroConstancia").value(startsWith("CNA-2026-")))
                .andReturn().getResponse().getContentAsString();
        String numero = JsonPath.read(emitida, "$.numeroConstancia");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM operativo.documento_buzon "
                + "WHERE numero_constancia = ? AND documento_contribuyente = '41028765'", Integer.class, numero))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT es_de_prueba FROM operativo.constancia WHERE numero_constancia = ?",
                Boolean.class, numero)).isTrue();
        mockMvc.perform(get(RUTA_OPERADOR).with(operador()))
                .andExpect(jsonPath("$[*].idSolicitud", not(hasItem((int) id))));
    }

    @Test
    void conDeudaLaVerificacionRechazaYMuestraElDetalle() throws Exception {
        long id = jdbc.queryForObject("""
                INSERT INTO operativo.solicitud_constancia (canal, estado, fecha_solicitud, documento_contribuyente)
                VALUES ('APP', 'FIRMADA', TIMESTAMP '2026-10-19 10:00:00', ?) RETURNING id_solicitud
                """, Long.class, RUC_CON_DEUDA);

        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo", containsString("deuda pendiente")))
                .andExpect(jsonPath("$.deuda.puedeSolicitar").value(false))
                .andExpect(jsonPath("$.deuda.obligaciones[0].deuda").isNumber())
                .andExpect(jsonPath("$.ordenPago").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operativo.transaccion_pago "
                + "WHERE id_solicitud_constancia = ?", Integer.class, id)).isZero();
    }

    @Test
    void noSeEmiteSinPagoYLaSecuenciaLaControlaElServidor() throws Exception {
        long id = solicitudFirmada(SIN_DEUDA);

        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/emision").with(operador()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PAGO_REQUERIDO"));
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SECUENCIA_INVALIDA"));
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/emision").with(operador()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PAGO_REQUERIDO"));
    }

    @Test
    void laSolicitudSinFirmarNoLlegaAlOperador() throws Exception {
        long id = iniciar(SIN_DEUDA);

        mockMvc.perform(get(RUTA_OPERADOR).with(operador()))
                .andExpect(jsonPath("$[*].idSolicitud", not(hasItem((int) id))));
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/rechazo").with(operador()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Datos incompletos\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_ENVIADA"));
    }

    @Test
    void elOperadorRechazaConMotivoYAnulaLaOrdenPendiente() throws Exception {
        long id = solicitudFirmada(SIN_DEUDA);
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/rechazo").with(operador()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/rechazo").with(operador()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Datos incompletos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Datos incompletos"))
                .andExpect(jsonPath("$.ordenPago.estado").value("RECHAZADA"));
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago/sesion").session(sesion(SIN_DEUDA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ORDEN_NO_PENDIENTE"));
    }

    @Test
    void sinPermisoNoSeGestionaYOtroContribuyenteNoVeLaOrden() throws Exception {
        long id = solicitudFirmada(SIN_DEUDA);

        mockMvc.perform(get(RUTA_OPERADOR))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_OPERADOR).with(user("analista")
                        .authorities(new SimpleGrantedAuthority("CONTRIBUYENTES:CONSULTAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA_OPERADOR + "/" + id + "/verificacion").with(operador()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get(RUTA_CONTRIBUYENTE + "/" + id + "/orden-pago").session(sesion("033218")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_ENCONTRADA"));
        mockMvc.perform(get(RUTA_OPERADOR + "/999999").with(operador()))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor operador() {
        return user("analista").authorities(new SimpleGrantedAuthority("CONSTANCIAS:GESTIONAR"));
    }

    private long solicitudFirmada(String codigoContribuyente) throws Exception {
        long id = iniciar(codigoContribuyente);
        mockMvc.perform(post(RUTA_CONTRIBUYENTE + "/" + id + "/firma").session(sesion(codigoContribuyente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmaBase64\": \"" + FIRMA_BASE64 + "\", \"certificado\": \"CERTIFICADO DE PRUEBA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADA"));
        return id;
    }

    private long iniciar(String codigoContribuyente) throws Exception {
        String respuesta = mockMvc.perform(post(RUTA_CONTRIBUYENTE).session(sesion(codigoContribuyente)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.idSolicitud")).longValue();
    }

    private static MockHttpSession sesion(String codigoContribuyente) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigoContribuyente);
        return sesion;
    }
}
