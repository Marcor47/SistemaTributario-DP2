package pe.municipalidad.tributario.regularidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/**
 * HU010: Calcular la regularidad de pago del contribuyente (% de cuotas pagadas a tiempo).
 */
@PruebaIntegracion
class RegularidadPagoControllerTest {

    private static final String RUTA = "/api/contribuyentes/{codigo}/regularidad";
    private static final String RUTA_ALIAS = "/api/regularidad/{codigo}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contribuyentePuntualTieneCienPorCientoDeRegularidad() throws Exception {
        mockMvc.perform(get(RUTA, "014502").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.nombreContribuyente").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.anio").value(2025))
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(16))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(16))
                .andExpect(jsonPath("$.cuotasPendientesOVencidas").value(0))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(100.00))
                .andExpect(jsonPath("$.segmentoSugerido").value("PUNTUAL"));
    }

    @Test
    void contribuyenteConMoraCalculaPorcentajeCorrecto() throws Exception {
        mockMvc.perform(get(RUTA, "021390").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("021390"))
                .andExpect(jsonPath("$.nombreContribuyente").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.anio").value(2025))
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(16))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(12))
                .andExpect(jsonPath("$.cuotasPendientesOVencidas").value(4))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(75.00))
                .andExpect(jsonPath("$.segmentoSugerido").value("REGULAR"));
    }

    @Test
    void cuandoNoSePasaAnioTomaElAnioActual() throws Exception {
        // En los tests, la fecha fija es 2026-10-19 -> anio 2026
        mockMvc.perform(get(RUTA, "014502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(8))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(8))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(100.00));
    }

    @Test
    void rutaAliasFuncionaCorrectamente() throws Exception {
        mockMvc.perform(get(RUTA_ALIAS, "021390").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("021390"))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(75.00));
    }

    @Test
    void contribuyenteSinCuotasEnElAnioDevuelveSinEmisiones() throws Exception {
        mockMvc.perform(get(RUTA, "014502").param("anio", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(0))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(0.00))
                .andExpect(jsonPath("$.segmentoSugerido").value("SIN_EMISIONES"));
    }

    @Test
    void contribuyenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(RUTA, "999999").param("anio", "2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
    }
}
