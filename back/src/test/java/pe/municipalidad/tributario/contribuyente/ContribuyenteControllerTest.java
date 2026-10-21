package pe.municipalidad.tributario.contribuyente;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/* HU001: consultar contribuyente por documento. */
@PruebaIntegracion
class ContribuyenteControllerTest {

    private static final String RUTA = "/api/contribuyentes";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void consultaPorDniDevuelveLaFichaDelContribuyente() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI").param("numeroDocumento", "41028765"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("014502"))
                .andExpect(jsonPath("$.nombre").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.tipoPersona").value("NATURAL"))
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumento").value("41028765"))
                .andExpect(jsonPath("$.domicilioFiscal").value("Av. La Marina 1450, San Miguel"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void consultaPorRucDevuelvePersonaJuridica() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "RUC").param("numeroDocumento", "20456781239"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("021390"))
                .andExpect(jsonPath("$.tipoPersona").value("JURIDICA"));
    }

    @Test
    void consultaPorCarneDeExtranjeriaToleraMinusculasYEspacios() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "CE").param("numeroDocumento", " 0012 34567 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("072405"))
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void documentoNoRegistradoDevuelve404() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI").param("numeroDocumento", "99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No existe un contribuyente registrado con DNI 99999999."));
    }

    @Test
    void mismoNumeroConOtroTipoDeDocumentoNoEncuentraAlContribuyente() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "CE").param("numeroDocumento", "041028765"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dniConLongitudIncorrectaDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI").param("numeroDocumento", "4102876"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("El DNI debe tener 8 dígitos."));
    }

    @Test
    void rucConPrefijoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "RUC").param("numeroDocumento", "30456781239"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));
    }

    @Test
    void tipoDeDocumentoDesconocidoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "PASAPORTE").param("numeroDocumento", "41028765"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles").value(hasItem("tipoDocumento: valor no permitido 'PASAPORTE'")));
    }

    @Test
    void faltaElNumeroDeDocumentoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles").value(hasItem("numeroDocumento: es obligatorio")));
    }

    @Test
    void numeroDeDocumentoEnBlancoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI").param("numeroDocumento", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles").value(hasItem("numeroDocumento: es obligatorio")));
    }
}
