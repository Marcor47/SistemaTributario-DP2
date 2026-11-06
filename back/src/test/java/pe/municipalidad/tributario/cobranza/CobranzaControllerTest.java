package pe.municipalidad.tributario.cobranza;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU013: consultar la etapa de cobranza de una obligación vencida.
 * Usa los datos ficticios de R__datos_prueba_03 y la fecha fija 2026-10-19.
 */
@PruebaInterna
@Transactional
class CobranzaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void obligacionExigibleEnViaCoactivaMuestraLaEtapaActualYTodoSuHistorial() throws Exception {
        long id = idDeObligacionVencida("021390", "IMPUESTO_PREDIAL", "2025-T4");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obligacion.contribuyenteCodigo").value("021390"))
                .andExpect(jsonPath("$.obligacion.contribuyenteNombre").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.obligacion.tributoDescripcion").value("Impuesto Predial"))
                .andExpect(jsonPath("$.obligacion.periodo").value("2025-T4"))
                .andExpect(jsonPath("$.obligacion.codigoPredio").isEmpty())
                .andExpect(jsonPath("$.obligacion.fechaVencimiento").value("2025-11-28"))
                .andExpect(jsonPath("$.obligacion.saldo").value(8723.75))
                .andExpect(jsonPath("$.fechaConsulta").value("2026-10-19"))
                .andExpect(jsonPath("$.diasVencida").value(325))
                .andExpect(jsonPath("$.etapaActual.etapa").value("COACTIVA"))
                .andExpect(jsonPath("$.etapaActual.nombre").value("Exigible en vía coactiva"))
                .andExpect(jsonPath("$.etapaActual.documentoReferencia").value("OP-2026-000101"))
                .andExpect(jsonPath("$.etapaActual.fechaFin").isEmpty())
                .andExpect(jsonPath("$.diasEnEtapaActual").value(229))
                .andExpect(jsonPath("$.historial", hasSize(5)))
                .andExpect(jsonPath("$.historial[0].etapa").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$.historial[0].registrada").value(false))
                .andExpect(jsonPath("$.historial[0].fechaInicio").value("2025-11-29"))
                .andExpect(jsonPath("$.historial[0].fechaFin").value("2026-01-12"))
                .andExpect(jsonPath("$.historial[1].etapa").value("CAMPANA"))
                .andExpect(jsonPath("$.historial[1].fechaFin").value("2026-01-20"))
                .andExpect(jsonPath("$.historial[2].etapa").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.historial[2].fechaFin").value("2026-02-03"))
                .andExpect(jsonPath("$.historial[3].etapa").value("ESPERA_LEGAL"))
                .andExpect(jsonPath("$.historial[3].observacion").value("Notificado en el domicilio fiscal"))
                .andExpect(jsonPath("$.historial[3].fechaFin").value("2026-03-04"))
                .andExpect(jsonPath("$.historial[4].etapa").value("COACTIVA"))
                .andExpect(jsonPath("$.historial[4].registrada").value(true));
    }

    @Test
    void obligacionSinEtapasRegistradasEstaEnMoraTempranaDesdeElDiaSiguienteAlVencimiento() throws Exception {
        /* 080245 no pagó la 3.ª cuota del predial, que venció el 31/08/2026. */
        long id = idDeObligacionVencida("080245", "IMPUESTO_PREDIAL", "2026-T3");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaActual.etapa").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$.etapaActual.registrada").value(false))
                .andExpect(jsonPath("$.etapaActual.fechaInicio").value("2026-09-01"))
                .andExpect(jsonPath("$.diasVencida").value(49))
                .andExpect(jsonPath("$.diasEnEtapaActual").value(48))
                .andExpect(jsonPath("$.historial", hasSize(1)));
    }

    @Test
    void moraTempranaRegistradaReemplazaALaImplicita() throws Exception {
        long id = idDeObligacionVencida("058934", "IMPUESTO_PREDIAL", "2026-T1");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaActual.etapa").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.historial", hasSize(2)))
                .andExpect(jsonPath("$.historial[0].etapa").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$.historial[0].registrada").value(true))
                .andExpect(jsonPath("$.historial[0].fechaInicio").value("2026-03-10"))
                .andExpect(jsonPath("$.historial[0].observacion").value("Aviso de cobranza enviado por correo"));
    }

    @Test
    void obligacionPagadaDevuelve422() throws Exception {
        long id = idDeObligacion("021390", "IMPUESTO_PREDIAL", 2025, 1);

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("OBLIGACION_NO_VENCIDA"))
                .andExpect(jsonPath("$.mensaje").value("La obligación Impuesto Predial 2025-T1 está pagada; solo las"
                        + " obligaciones vencidas tienen etapa de cobranza."));
    }

    @Test
    void obligacionQueAunNoVenceDevuelve422() throws Exception {
        /* La 4.ª cuota del predial 2026 vence el 30/11/2026. */
        long id = idDeObligacion("045067", "IMPUESTO_PREDIAL", 2026, 4);

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("OBLIGACION_NO_VENCIDA"))
                .andExpect(jsonPath("$.mensaje").value(containsString("está pendiente")));
    }

    @Test
    void obligacionInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/obligaciones/999999/etapa-cobranza"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBLIGACION_NO_ENCONTRADA"));
    }

    @Test
    void listaLasObligacionesVencidasOrdenadasPorVencimientoConSuEtapa() throws Exception {
        mockMvc.perform(get("/api/contribuyentes/021390/obligaciones-vencidas"))
                .andExpect(status().isOk())
                /* 5 cuotas de predial y 14 meses de arbitrios en cada uno de sus 11 predios. */
                .andExpect(jsonPath("$", hasSize(159)))
                .andExpect(jsonPath("$[0].periodo").value("2025-08"))
                .andExpect(jsonPath("$[0].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$[0].codigoPredio").value("021390-01"))
                .andExpect(jsonPath("$[0].etapaActual").value("ESPERA_LEGAL"))
                .andExpect(jsonPath("$[1].codigoPredio").value("021390-02"))
                .andExpect(jsonPath("$[1].etapaActual").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$[11].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$[11].periodo").value("2025-T3"))
                .andExpect(jsonPath("$[11].etapaActual").value("COACTIVA"))
                .andExpect(jsonPath("$[11].etapaNombre").value("Exigible en vía coactiva"))
                .andExpect(jsonPath("$[11].diasVencida").value(416))
                .andExpect(jsonPath("$[158].periodo").value("2026-09"));
    }

    @Test
    void contribuyenteAlDiaNoTieneObligacionesVencidas() throws Exception {
        mockMvc.perform(get("/api/contribuyentes/014502/obligaciones-vencidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void contribuyenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/contribuyentes/000000/obligaciones-vencidas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
    }

    private long idDeObligacionVencida(String contribuyenteCodigo, String tributo, String periodo) throws Exception {
        String listado = mockMvc.perform(get("/api/contribuyentes/" + contribuyenteCodigo + "/obligaciones-vencidas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(listado,
                "$[?(@.tributo == '" + tributo + "' && @.periodo == '" + periodo + "')].id");
        return ids.getFirst().longValue();
    }

    /* Para obligaciones que no aparecen en el listado de vencidas (pagadas o pendientes). */
    private long idDeObligacion(String contribuyenteCodigo, String tributo, int anio, int numeroCuota) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM samnet.obligacion
                WHERE contribuyente_codigo = ? AND tributo = ? AND anio = ? AND numero_cuota = ?
                """, Long.class, contribuyenteCodigo, tributo, anio, numeroCuota);
    }
}
