package pe.municipalidad.tributario.documentos;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/* HU016: preferencias de aviso del contribuyente (P12). Los datos salen de los contribuyentes de prueba. */
@PruebaIntegracion
@Transactional
class PreferenciaAvisoControllerTest {

    private static final String RUTA = "/api/portal/preferencias-avisos";

    @Autowired
    MockMvc mvc;

    @Test
    void sinSesionNoSeAccedeALasPreferencias() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordatorioVencimiento\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devuelveLosDatosDelContribuyenteYLaAnticipacionVigente() throws Exception {
        mvc.perform(get(RUTA).session(sesion("014502")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.nombreRazonSocial").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumentoEnmascarado").value("****8765"))
                .andExpect(jsonPath("$.correo").value("maria.garcia@correo.test"))
                .andExpect(jsonPath("$.recordatorioVencimiento").value(true))
                .andExpect(jsonPath("$.diasAnticipacion").value(5));
    }

    @Test
    void guardaLaPreferenciaYLaDevuelveEnLaSiguienteConsulta() throws Exception {
        mvc.perform(put(RUTA).session(sesion("014502")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordatorioVencimiento\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorioVencimiento").value(false));
        mvc.perform(get(RUTA).session(sesion("014502")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorioVencimiento").value(false));
        mvc.perform(put(RUTA).session(sesion("014502")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordatorioVencimiento\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorioVencimiento").value(true));
    }

    @Test
    void unContribuyenteSinCorreoNoPuedeActivarElRecordatorio() throws Exception {
        mvc.perform(get(RUTA).session(sesion("033218")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(nullValue()))
                .andExpect(jsonPath("$.recordatorioVencimiento").value(false));
        mvc.perform(put(RUTA).session(sesion("033218")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordatorioVencimiento\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_REGISTRADO"));
    }

    @Test
    void exigeElValorDelRecordatorio() throws Exception {
        mvc.perform(put(RUTA).session(sesion("014502")).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession sesion(String codigoContribuyente) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigoContribuyente);
        return sesion;
    }
}
