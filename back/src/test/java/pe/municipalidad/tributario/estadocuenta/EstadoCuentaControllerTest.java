package pe.municipalidad.tributario.estadocuenta;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;
import pe.municipalidad.tributario.auth.SesionContribuyente;

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
        mockMvc.perform(get("/api/estado-cuenta/021390").session(sesion("021390")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("021390"))
                .andExpect(jsonPath("$.contribuyente.tipoDoc").value("RUC"))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.desactualizado").value(false))
                /* 8 cuotas de predial y 258 meses de arbitrios de sus 11 predios (el 021390-03 desde julio de 2025). */
                .andExpect(jsonPath("$.filas", hasSize(266)))
                .andExpect(jsonPath("$.filas[0].tributo").value("Impuesto Predial"))
                .andExpect(jsonPath("$.filas[0].periodo").value("2025-T1"))
                .andExpect(jsonPath("$.filas[0].fechaVencimiento").value("2025-02-28"))
                .andExpect(jsonPath("$.filas[0].estado").value("Pagada"))
                .andExpect(jsonPath("$.filas[1].tributoCodigo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.filas[1].periodo").value("2025-01"))
                .andExpect(jsonPath("$.filas[1].codigoPredio").value("021390-01"))
                .andExpect(jsonPath("$.filas[1].servicios", hasSize(4)))
                .andExpect(jsonPath("$.predios", hasSize(11)));
    }

    /*
     * Lo que pidió el JP: por cada año se sabe cuánto es Impuesto Predial y cuánto arbitrios. 021390 tiene en 2025
     * diez predios al 1 de enero (S/ 3 650 000): predial S/ 34 895,00 (escala 0,2 % / 0,6 % / 1 % sobre la UIT de
     * S/ 5 350) en 4 cuotas de S/ 8 723,75; el 021390-03, comprado en junio, solo paga arbitrios desde julio y entra
     * a la base del predial en 2026 (S/ 3 860 000 con UIT de S/ 5 500: S/ 36 950,00).
     */
    @Test
    void separaPorAnioElImpuestoPredialDeLosArbitriosYCalculaElInteresMoratorio() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/021390").session(sesion("021390")))
                .andExpect(jsonPath("$.timMensual").value(0.90))
                .andExpect(jsonPath("$.resumenAnual[0].anio").value(2025))
                .andExpect(jsonPath("$.resumenAnual[0].impuestoPredial.emitido").value(34895.00))
                .andExpect(jsonPath("$.resumenAnual[0].arbitrios.emitido").value(12799.56))
                .andExpect(jsonPath("$.resumenAnual[0].total.emitido").value(47694.56))
                .andExpect(jsonPath("$.resumenAnual[1].impuestoPredial.emitido").value(36950.00))
                /* 3.ª cuota 2025: S/ 8 723,75 vencida el 29/08/2025, 416 días al 19/10/2026 x 0,03 % diario. */
                .andExpect(jsonPath("$.filas[?(@.periodo == '2025-T3')].diasVencida").value(416))
                .andExpect(jsonPath("$.filas[?(@.periodo == '2025-T3')].interesMoratorio").value(1088.72))
                .andExpect(jsonPath("$.filas[?(@.periodo == '2025-T3')].totalAPagar").value(9812.47))
                .andExpect(jsonPath("$.filas[?(@.periodo == '2025-T3')].etapaCobranza")
                        .value("Exigible en vía coactiva"));
    }

    /* El analista consulta cualquier cuenta por la ruta interna; sin CUENTAS:CONSULTAR no puede. */
    @Test
    void elAnalistaConsultaLaCuentaDeCualquierContribuyente() throws Exception {
        mockMvc.perform(get("/api/contribuyentes/058934/estado-cuenta")
                        .with(user("analista").authorities(new SimpleGrantedAuthority("CUENTAS:CONSULTAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("058934"))
                .andExpect(jsonPath("$.predios", hasSize(2)))
                .andExpect(jsonPath("$.predios[0].porcentajePropiedad").value(50.0));
        mockMvc.perform(get("/api/contribuyentes/058934/estado-cuenta")
                        .with(user("analista").authorities(new SimpleGrantedAuthority("CONTRIBUYENTES:CONSULTAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contribuyentes/058934/estado-cuenta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscaContribuyentesPorTextoParcial() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/contribuyentes").param("q", "medina")
                        .session(sesion("058934")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("MEDINA CRUZ, PATRICIA"));
    }

    @Test
    void siSamnetNoRespondeMuestraLaUltimaCopiaComoDesactualizada() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/014502").session(sesion("014502"))).andExpect(status().isOk());
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerEstadoCuenta(anyString());

        mockMvc.perform(get("/api/estado-cuenta/014502").session(sesion("014502")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizado").value(true))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.contribuyente.codigo").value("014502"))
                .andExpect(jsonPath("$.filas", hasSize(56)))
                .andExpect(jsonPath("$.resumenAnual", hasSize(2)));
    }

    @Test
    void sinCopiaPreviaInformaQueSamnetNoEstaDisponible() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerEstadoCuenta(anyString());

        mockMvc.perform(get("/api/estado-cuenta/033218").session(sesion("033218")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    @Test
    void contribuyenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/000000").session(sesion("000000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
    }

    @Test
    void sinSesionNiLaBusquedaNiElDetalleRevelanDatos() throws Exception {
        mockMvc.perform(get("/api/estado-cuenta/021390"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/estado-cuenta/contribuyentes").param("q", "MEDINA"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/estado-cuenta/021390").session(sesion("014502")))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession sesion(String codigo) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigo);
        return sesion;
    }
}
