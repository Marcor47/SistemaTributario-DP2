package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class PagoNiubizConfirmacionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean NiubizGateway pasarela;
    private final MockHttpSession sesion = new MockHttpSession();
    private String orden;
    private BigDecimal monto;

    @BeforeEach
    void configurar() {
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, "021390");
        when(pasarela.crearOrdenPago(any(), anyString(), any(), anyString())).thenAnswer(i -> {
            monto = i.getArgument(0);
            orden = i.<String>getArgument(1).substring("OBLIGACIONES-".length());
            return new NiubizGateway.OrdenPago(orden, null, new NiubizGateway.Checkout("sesion", "123456789", orden,
                    "https://static-content-qas.vnforapps.com/env/sandbox/js/checkout.js",
                    "https://municipalidad.example/retorno", "https://municipalidad.example/timeout"));
        });
    }

    @Test
    void tokenAutorizadoActualizaCuentaYComprobanteSoloUnaVez() throws Exception {
        int id = iniciar();
        when(pasarela.autorizar(orden, monto, "token-niubiz"))
                .thenReturn(new NiubizGateway.EstadoPagoPasarela(orden, true, "ABC123", monto));
        confirmar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.comprobante").value("WEB-" + id));
        confirmar(id).andExpect(status().isOk());
        verify(pasarela, times(1)).autorizar(orden, monto, "token-niubiz");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago=?", Integer.class, "WEB-" + id)).isEqualTo(1);
        mvc.perform(get("/api/portal/estado-cuenta").session(sesion)).andExpect(status().isOk());
        assertThat(saldoPendiente()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void pagoNoAutorizadoConservaLaDeudaPendiente() throws Exception {
        comprobarSinRegistro(false, false);
    }

    @Test
    void unImporteAutorizadoDiferenteTampocoSeRegistra() throws Exception {
        comprobarSinRegistro(true, true);
    }

    private void comprobarSinRegistro(boolean autorizado, boolean alterarMonto) throws Exception {
        BigDecimal saldo = saldoPendiente();
        int id = iniciar();
        when(pasarela.autorizar(orden, monto, "token-niubiz"))
                .thenReturn(new NiubizGateway.EstadoPagoPasarela(orden, autorizado, "ABC123",
                        alterarMonto ? monto.add(BigDecimal.ONE) : monto));
        confirmar(id).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PAGO_NO_AUTORIZADO"));
        assertThat(saldoPendiente()).isEqualByComparingTo(saldo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago=?", Integer.class, "WEB-" + id)).isZero();
        mvc.perform(get("/api/portal/pagos/" + id).session(sesion)).andExpect(jsonPath("$.estado").value("INICIADA"));
    }

    private BigDecimal saldoPendiente() {
        return jdbc.queryForObject("SELECT sum(monto_emitido-monto_pagado-monto_descontado) FROM samnet.obligacion WHERE contribuyente_codigo='021390'", BigDecimal.class);
    }
    private int iniciar() throws Exception {
        return JsonPath.read(mvc.perform(post("/api/portal/pagos").session(sesion).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proveedor").value("NIUBIZ_SANDBOX"))
                .andReturn().getResponse().getContentAsString(), "$.idTransaccion");
    }
    private org.springframework.test.web.servlet.ResultActions confirmar(int id) throws Exception {
        return mvc.perform(post("/api/portal/pagos/" + id + "/confirmacion").session(sesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"transactionToken\":\"token-niubiz\"}"));
    }
}
