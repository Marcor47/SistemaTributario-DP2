package pe.municipalidad.tributario.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
class PagoPortalConcurrenciaTest {
    private static final String CODIGO = "990022";
    private static final String DOCUMENTO = "99002200";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM samnet.pago_obligacion WHERE id_pago IN "
                + "(SELECT id_pago FROM samnet.pago WHERE contribuyente_codigo=?)", CODIGO);
        jdbc.update("DELETE FROM samnet.pago WHERE contribuyente_codigo=?", CODIGO);
        jdbc.update("DELETE FROM operativo.transaccion_pago_detalle WHERE id_transaccion IN "
                + "(SELECT id_transaccion FROM operativo.transaccion_pago WHERE documento_contribuyente=?)", DOCUMENTO);
        jdbc.update("DELETE FROM operativo.transaccion_obligacion WHERE id_transaccion IN "
                + "(SELECT id_transaccion FROM operativo.transaccion_pago WHERE documento_contribuyente=?)", DOCUMENTO);
        jdbc.update("DELETE FROM operativo.transaccion_pago WHERE documento_contribuyente=?", DOCUMENTO);
        jdbc.update("DELETE FROM samnet.obligacion WHERE contribuyente_codigo=?", CODIGO);
        jdbc.update("DELETE FROM samnet.contribuyente WHERE codigo=?", CODIGO);
    }

    @Test
    void dosConfirmacionesConcurrentesSoloRegistranUnPago() throws Exception {
        jdbc.update("INSERT INTO samnet.contribuyente "
                + "(codigo, nombre, tipo_persona, tipo_documento, numero_documento) "
                + "VALUES (?, 'PRUEBA DE CONCURRENCIA', 'NATURAL', 'DNI', ?)", CODIGO, DOCUMENTO);
        jdbc.update("INSERT INTO samnet.obligacion "
                + "(contribuyente_codigo, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado) "
                + "VALUES (?, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31', 100, 0), "
                + "(?, 'IMPUESTO_PREDIAL', 2026, 2, DATE '2026-06-30', 150, 0)", CODIGO, CODIGO);
        int primera = crearOrden();
        int segunda = crearOrden();
        CountDownLatch salida = new CountDownLatch(1);
        Callable<Integer> confirmarPrimera = () -> confirmar(primera, salida);
        Callable<Integer> confirmarSegunda = () -> confirmar(segunda, salida);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var uno = hilos.submit(confirmarPrimera);
            var dos = hilos.submit(confirmarSegunda);
            salida.countDown();
            assertThat(List.of(uno.get(), dos.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samnet.pago WHERE id_pago IN (?, ?)",
                Integer.class, "WEB-" + primera, "WEB-" + segunda)).isEqualTo(1);
    }

    private int crearOrden() throws Exception {
        var respuesta = mvc.perform(post("/api/portal/pagos").session(sesion())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();
        assertThat(respuesta.getStatus()).isEqualTo(201);
        return JsonPath.read(respuesta.getContentAsString(), "$.idTransaccion");
    }

    private int confirmar(int id, CountDownLatch salida) throws Exception {
        salida.await();
        return mvc.perform(post("/api/portal/pagos/" + id + "/confirmacion").session(sesion()))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpSession sesion() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, CODIGO);
        return sesion;
    }
}
