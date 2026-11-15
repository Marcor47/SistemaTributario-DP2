package pe.municipalidad.tributario.regularidad;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/* HU021 (RF-035): clasificación del riesgo de mora con su probabilidad y los factores que la explican. */
@PruebaInterna
class RiesgoMoraControllerTest {

    private static final String RUTA = "/api/contribuyentes/{codigo}/riesgo-mora";

    @Autowired
    private MockMvc mockMvc;

    /* 014502 paga todo a tiempo: sin cuotas impagas ni atrasos, el riesgo es bajo. */
    @Test
    void unContribuyentePuntualTieneRiesgoBajo() throws Exception {
        mockMvc.perform(get(RUTA, "014502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.categoria").value("BAJO"))
                .andExpect(jsonPath("$.cuotasImpagas").value(0))
                .andExpect(jsonPath("$.probabilidad", lessThan(0.30)))
                .andExpect(jsonPath("$.factores", not(empty())));
    }

    /* 021390 dejó impagas sus cuotas desde agosto de 2025: el riesgo sube a medio o alto. */
    @Test
    void unContribuyenteConCuotasImpagasNoQuedaEnRiesgoBajo() throws Exception {
        mockMvc.perform(get(RUTA, "021390"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria", oneOf("MEDIO", "ALTO")))
                .andExpect(jsonPath("$.cuotasImpagas", greaterThan(0)))
                .andExpect(jsonPath("$.deudaVencida", greaterThan(0.0)));
    }

    @Test
    void unCodigoInexistenteResponde404() throws Exception {
        mockMvc.perform(get(RUTA, "999999"))
                .andExpect(status().isNotFound());
    }
}
