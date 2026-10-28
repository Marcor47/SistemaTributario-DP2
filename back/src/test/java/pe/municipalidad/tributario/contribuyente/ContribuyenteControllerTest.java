package pe.municipalidad.tributario.contribuyente;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
    void laFichaIncluyeLosImpuestosResumidosPorTributoYDetalladosPorPeriodo() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "DNI").param("numeroDocumento", "41028765"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impuestos.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.impuestos.totalEmitido").value(2583.0))
                .andExpect(jsonPath("$.impuestos.totalPagado").value(2583.0))
                .andExpect(jsonPath("$.impuestos.totalDeuda").value(0.0))
                .andExpect(jsonPath("$.impuestos.porTributo", hasSize(2)))
                .andExpect(jsonPath("$.impuestos.porTributo[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.impuestos.porTributo[0].emitido").value(1134.0))
                .andExpect(jsonPath("$.impuestos.porTributo[1].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.impuestos.porTributo[1].descripcion").value("Arbitrios Municipales"))
                .andExpect(jsonPath("$.impuestos.porTributo[1].emitido").value(1449.0))
                .andExpect(jsonPath("$.impuestos.cuotas", hasSize(12)))
                .andExpect(jsonPath("$.impuestos.cuotas[0].periodo").value("2025-T1"))
                .andExpect(jsonPath("$.impuestos.cuotas[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.impuestos.cuotas[1].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.impuestos.cuotas[1].emitido").value(230.0))
                .andExpect(jsonPath("$.impuestos.cuotas[1].estado").value("PAGADA"));
    }

    @Test
    void losImpuestosMuestranLaDeudaVencidaDelContribuyente() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "RUC").param("numeroDocumento", "20456781239"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impuestos.totalDeuda").value(6907.68))
                .andExpect(jsonPath("$.impuestos.porTributo[0].deuda").value(3032.64))
                .andExpect(jsonPath("$.impuestos.porTributo[1].deuda").value(3875.04))
                .andExpect(jsonPath("$.impuestos.cuotas[6].periodo").value("2025-T4"))
                .andExpect(jsonPath("$.impuestos.cuotas[6].deuda").value(992.16))
                .andExpect(jsonPath("$.impuestos.cuotas[6].estado").value("VENCIDA"));
    }

    @Test
    void consultaPorCarneDeExtranjeriaToleraMinusculasYEspacios() throws Exception {
        mockMvc.perform(get(RUTA).param("tipoDocumento", "CE").param("numeroDocumento", " 0012 34567 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("072405"))
                .andExpect(jsonPath("$.estado").value("INACTIVO"))
                .andExpect(jsonPath("$.impuestos.totalDeuda").value(0.0))
                .andExpect(jsonPath("$.impuestos.cuotas", hasSize(0)));
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
