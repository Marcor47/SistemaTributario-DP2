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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU013: consultar la etapa de cobranza de una obligación vencida.
 * Usa los datos ficticios de R__datos_prueba_03 y la fecha fija 2026-10-19.
 */
@PruebaIntegracion
@Transactional
class CobranzaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void obligacionEnCoactivaMuestraLaEtapaActualYTodoSuHistorial() throws Exception {
        long id = idDeObligacionVencida("021390", "IMPUESTO_PREDIAL", "2025-T4");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obligacion.contribuyenteCodigo").value("021390"))
                .andExpect(jsonPath("$.obligacion.contribuyenteNombre").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.obligacion.tributoDescripcion").value("Impuesto Predial"))
                .andExpect(jsonPath("$.obligacion.periodo").value("2025-T4"))
                .andExpect(jsonPath("$.obligacion.codigoPredio").isEmpty())
                .andExpect(jsonPath("$.obligacion.fechaVencimiento").value("2025-12-31"))
                .andExpect(jsonPath("$.obligacion.saldo").value(992.16))
                .andExpect(jsonPath("$.fechaConsulta").value("2026-10-19"))
                .andExpect(jsonPath("$.diasVencida").value(292))
                .andExpect(jsonPath("$.etapaActual.etapa").value("COACTIVA"))
                .andExpect(jsonPath("$.etapaActual.nombre").value("Cobranza coactiva"))
                .andExpect(jsonPath("$.etapaActual.documentoReferencia").value("REC-2026-000045"))
                .andExpect(jsonPath("$.etapaActual.fechaFin").isEmpty())
                .andExpect(jsonPath("$.diasEnEtapaActual").value(160))
                .andExpect(jsonPath("$.historial", hasSize(4)))
                .andExpect(jsonPath("$.historial[0].etapa").value("ORDINARIA"))
                .andExpect(jsonPath("$.historial[0].registrada").value(false))
                .andExpect(jsonPath("$.historial[0].fechaInicio").value("2026-01-01"))
                .andExpect(jsonPath("$.historial[0].fechaFin").value("2026-01-20"))
                .andExpect(jsonPath("$.historial[1].etapa").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.historial[1].fechaFin").value("2026-02-03"))
                .andExpect(jsonPath("$.historial[2].etapa").value("VALOR_NOTIFICADO"))
                .andExpect(jsonPath("$.historial[2].observacion").value("Notificado en el domicilio fiscal"))
                .andExpect(jsonPath("$.historial[3].etapa").value("COACTIVA"))
                .andExpect(jsonPath("$.historial[3].registrada").value(true));
    }

    @Test
    void obligacionSinEtapasRegistradasEstaEnCobranzaOrdinariaDesdeElDiaSiguienteAlVencimiento() throws Exception {
        long id = idDeObligacionVencida("045067", "IMPUESTO_PREDIAL", "2026-T2");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaActual.etapa").value("ORDINARIA"))
                .andExpect(jsonPath("$.etapaActual.registrada").value(false))
                .andExpect(jsonPath("$.etapaActual.fechaInicio").value("2026-07-01"))
                .andExpect(jsonPath("$.diasVencida").value(111))
                .andExpect(jsonPath("$.diasEnEtapaActual").value(110))
                .andExpect(jsonPath("$.historial", hasSize(1)));
    }

    @Test
    void cobranzaOrdinariaRegistradaReemplazaALaImplicita() throws Exception {
        long id = idDeObligacionVencida("058934", "IMPUESTO_PREDIAL", "2026-T1");

        mockMvc.perform(get("/api/obligaciones/" + id + "/etapa-cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaActual.etapa").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.historial", hasSize(2)))
                .andExpect(jsonPath("$.historial[0].etapa").value("ORDINARIA"))
                .andExpect(jsonPath("$.historial[0].registrada").value(true))
                .andExpect(jsonPath("$.historial[0].fechaInicio").value("2026-04-10"))
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
    @Sql(statements = """
            INSERT INTO tributario.obligacion
                (contribuyente_codigo, tributo, anio, numero_cuota, fecha_vencimiento, monto_emitido, monto_pagado)
            VALUES ('014502', 'IMPUESTO_PREDIAL', 2026, 4, DATE '2026-12-31', 205.20, 0);
            """)
    void obligacionQueAunNoVenceDevuelve422() throws Exception {
        long id = idDeObligacion("014502", "IMPUESTO_PREDIAL", 2026, 4);

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
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].periodo").value("2025-T4"))
                .andExpect(jsonPath("$[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$[0].etapaActual").value("COACTIVA"))
                .andExpect(jsonPath("$[0].etapaNombre").value("Cobranza coactiva"))
                .andExpect(jsonPath("$[0].diasVencida").value(292))
                .andExpect(jsonPath("$[1].tributo").value("LIMPIEZA_PUBLICA"))
                .andExpect(jsonPath("$[1].codigoPredio").value("021390-01"))
                .andExpect(jsonPath("$[1].etapaActual").value("VALOR_NOTIFICADO"))
                .andExpect(jsonPath("$[2].etapaActual").value("ORDINARIA"))
                .andExpect(jsonPath("$[11].periodo").value("2026-T2"));
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
                SELECT id FROM tributario.obligacion
                WHERE contribuyente_codigo = ? AND tributo = ? AND anio = ? AND numero_cuota = ?
                """, Long.class, contribuyenteCodigo, tributo, anio, numeroCuota);
    }
}
