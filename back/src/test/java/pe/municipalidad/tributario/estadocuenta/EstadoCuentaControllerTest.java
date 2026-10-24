package pe.municipalidad.tributario.estadocuenta;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/* HU004: estado de cuenta desde SAMNET y contingencia con la última copia (RNF-002). */
@PruebaIntegracion
@Transactional
class EstadoCuentaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SamnetGateway samnet;

    @Test
    void devuelveElEstadoDeCuentaDesglosadoConFechaDeCorte() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/021390"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("021390"))
                .andExpect(jsonPath("$.contribuyente.tipoDoc").value("RUC"))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.desactualizado").value(false))
                .andExpect(jsonPath("$.filas", hasSize(24)))
                .andExpect(jsonPath("$.filas[0].tributo").value("Impuesto Predial"))
                .andExpect(jsonPath("$.filas[0].periodo").value("2025-T1"))
                .andExpect(jsonPath("$.filas[0].estado").value("Pagada"));
    }

    @Test
    void buscaContribuyentesPorTextoParcial() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/contribuyentes").param("q", "medina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("MEDINA CRUZ, PATRICIA"));
    }

    @Test
    void siSamnetNoRespondeMuestraLaUltimaCopiaComoDesactualizada() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/014502")).andExpect(status().isOk());
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerEstadoCuenta(anyString());

        mockMvc.perform(get("/api/estado-cuenta/014502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizado").value(true))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.contribuyente.codigo").value("014502"))
                .andExpect(jsonPath("$.filas", hasSize(24)));
    }

    @Test
    void sinCopiaPreviaInformaQueSamnetNoEstaDisponible() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerEstadoCuenta(anyString());

        mockMvc.perform(get("/api/estado-cuenta/033218"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    @Test
    void contribuyenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
    }
}
