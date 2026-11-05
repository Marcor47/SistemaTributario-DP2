package pe.municipalidad.tributario.predios;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU002: consulta de predios contra SAMNET sintético. La inmobiliaria 080245 tiene 31 predios (30 departamentos y un
 * local); el 058934-01 es de dos copropietarios al 50 %.
 */
@PruebaIntegracion
class PredioConsultaControllerTest {

    private static final String RUTA = "/api/predios";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unContribuyenteConMuchosPrediosSeRecorrePorPaginas() throws Exception {
        mockMvc.perform(get(RUTA).param("consulta", "20601234567").param("tamano", "10").with(analista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(31))
                .andExpect(jsonPath("$.totalPaginas").value(4))
                .andExpect(jsonPath("$.contenido", hasSize(10)))
                .andExpect(jsonPath("$.contenido[0].codigoPredio").value("080245-01"))
                .andExpect(jsonPath("$.contenido[0].titulares[0].nombre").value("INMOBILIARIA MIRAMAR S.A.C."));

        mockMvc.perform(get(RUTA).param("consulta", "080245").param("pagina", "3").param("tamano", "10")
                        .with(analista()))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usoPredio").value("Comercio"));
    }

    @Test
    void buscaPorCodigoDireccionOTitularYMuestraLaCopropiedad() throws Exception {
        mockMvc.perform(get(RUTA).param("consulta", "058934-01").with(analista()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].titulares", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].titulares[0].porcentajePropiedad").value(50.0));
        mockMvc.perform(get(RUTA).param("consulta", "los cedros").with(analista()))
                .andExpect(jsonPath("$.contenido[0].codigoPredio").value("014502-02"));
        mockMvc.perform(get(RUTA).param("consulta", "GARCÍA TORRES").with(analista()))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(get(RUTA).param("consulta", "inexistente").with(analista()))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
    }

    @Test
    void validaLaConsultaYExigePermiso() throws Exception {
        mockMvc.perform(get(RUTA).param("consulta", "080245"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA).param("consulta", " ").with(analista()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONSULTA_INVALIDA"));
        mockMvc.perform(get(RUTA).param("consulta", "080245").param("tamano", "500").with(analista()))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor analista() {
        return user("analista").authorities(new SimpleGrantedAuthority("PREDIOS:CONSULTAR"));
    }
}
