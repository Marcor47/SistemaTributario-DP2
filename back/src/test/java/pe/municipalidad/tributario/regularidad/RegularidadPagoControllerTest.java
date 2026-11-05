package pe.municipalidad.tributario.regularidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/**
 * HU010: Calcular la regularidad de pago del contribuyente (% de cuotas pagadas a tiempo).
 */
@PruebaInterna
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
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(28))
                .andExpect(jsonPath("$.cuotasExigibles").value(28))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(28))
                .andExpect(jsonPath("$.cuotasPendientesOVencidas").value(0))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(100.00))
                .andExpect(jsonPath("$.segmentoSugerido").value("PUNTUAL"));
    }

    /*
     * 021390 en 2025: 130 cuotas (4 de predial; 12 meses de arbitrios de 10 predios y 6 del 021390-03, comprado en
     * junio). Pagó a tiempo 42, con atraso 31 (la 2.ª cuota del predial y febrero, abril y junio de los 10 predios) y
     * dejó impagas 57 desde agosto.
     */
    @Test
    void contribuyenteConMoraCalculaPorcentajeCorrecto() throws Exception {
        mockMvc.perform(get(RUTA, "021390").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("021390"))
                .andExpect(jsonPath("$.nombreContribuyente").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.anio").value(2025))
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(130))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(42))
                .andExpect(jsonPath("$.cuotasPagadasConAtraso").value(31))
                .andExpect(jsonPath("$.cuotasPendientesOVencidas").value(57))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(32.31))
                .andExpect(jsonPath("$.segmentoSugerido").value("MOROSO"));
    }

    /* 045067 paga antes del vencimiento salvo la 3.ª cuota del predial y marzo y julio de arbitrios: 13 de 16. */
    @Test
    void unPagoAtrasadoYaNoCuentaComoPuntual() throws Exception {
        mockMvc.perform(get(RUTA, "045067").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(13))
                .andExpect(jsonPath("$.cuotasPagadasConAtraso").value(3))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(81.25))
                .andExpect(jsonPath("$.segmentoSugerido").value("REGULAR"));
    }

    @Test
    void cuandoNoSePasaAnioTomaElAnioActual() throws Exception {
        /* En los tests la fecha fija es 2026-10-19: año 2026. */
        mockMvc.perform(get(RUTA, "014502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.anio").value(2026))
                /* Las cuotas que aún no vencen (4.ª del predial, octubre a diciembre) no cuentan. */
                .andExpect(jsonPath("$.totalCuotasEmitidas").value(28))
                .andExpect(jsonPath("$.cuotasExigibles").value(21))
                .andExpect(jsonPath("$.cuotasPagadasATiempo").value(21))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(100.00));
    }

    @Test
    void rutaAliasFuncionaCorrectamente() throws Exception {
        mockMvc.perform(get(RUTA_ALIAS, "021390").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("021390"))
                .andExpect(jsonPath("$.porcentajeRegularidad").value(32.31));
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
