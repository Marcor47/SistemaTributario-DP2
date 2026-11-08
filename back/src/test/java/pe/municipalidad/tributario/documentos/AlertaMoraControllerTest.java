package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import pe.municipalidad.tributario.integracion.correo.CorreoSimuladoGateway;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU012: Enviar alerta de mora temprana por correo (RF-013).
 * Criterios de aceptación:
 * 1. Dado que una carga del ETL detecta una cuota vencida sin pago, cuando el contribuyente tiene correo
 *    registrado, entonces recibe el aviso dentro de las 24 horas.
 * 2. Dado el envío del correo, cuando se completa, entonces queda registrado en el historial del contribuyente.
 * RF-013: Solo por correo; WhatsApp, SMS e IVR quedan fuera. Quienes no tienen correo registrado quedan excluidos.
 * Dependencia de HU026: Solo lotes publicados y validados por calidad generan alertas. Lotes rechazados no las generan.
 */
@PruebaInterna
@Transactional
class AlertaMoraControllerTest {

    private static final String RUTA_EVALUACIONES = "/api/alertas-mora/evaluaciones";
    private static final String RUTA_ALERTAS = "/api/alertas-mora";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private AvisoCorreoRepository avisos;

    @Autowired
    private CorreoGateway correoGateway;

    @Test
    void cargaEtlDetectaCuotaVencidaYEnviaAvisoPorCorreoSiContribuyenteTieneCorreoRegistrado() throws Exception {
        LoteETL lote = lotePublicado();

        // 014502 tiene correo registrado (maria.garcia@correo.test)
        // Cuota vencida: vencimiento 2026-09-30 <= fecha de corte 2026-10-19 con insoluto 150.00
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-7001",
                            "contribuyente_codigo": "014502",
                            "fecha_vencimiento": "2026-09-30",
                            "monto_emitido": 200.00,
                            "monto_pagado": 50.00,
                            "insoluto": 150.00
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.cuotasEvaluadas").value(1))
                .andExpect(jsonPath("$.cuotasVencidasDetectadas").value(1))
                .andExpect(jsonPath("$.alertasEnviadas").value(1))
                .andExpect(jsonPath("$.sinCorreoRegistrado").value(0))
                .andExpect(jsonPath("$.avisos", hasSize(1)))
                .andExpect(jsonPath("$.avisos[0].tipo").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$.avisos[0].documentoContribuyente").value("41028765"))
                .andExpect(jsonPath("$.avisos[0].correoDestino").value("maria.garcia@correo.test"))
                .andExpect(jsonPath("$.avisos[0].estado").value("ENVIADO"))
                .andExpect(jsonPath("$.avisos[0].idObligacion").value("OBL-7001"))
                .andExpect(jsonPath("$.avisos[0].fechaEnvio").isNotEmpty());

        if (correoGateway instanceof CorreoSimuladoGateway simulado) {
            assertThat(simulado.getEnviados()).anyMatch(c -> c.destino().equals("maria.garcia@correo.test")
                    && c.asunto().contains("OBL-7001"));
        }
    }

    @Test
    void elEnvioCompletadoQuedaRegistradoEnElHistorialDelContribuyente() throws Exception {
        LoteETL lote = lotePublicado();

        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-8001",
                            "contribuyente_codigo": "021390",
                            "fecha_vencimiento": "2026-08-31",
                            "insoluto": 320.50
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        // Consultar historial por código de contribuyente (/api/contribuyentes/021390/avisos-mora)
        mockMvc.perform(get("/api/contribuyentes/021390/avisos-mora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$[0].documentoContribuyente").value("20456781239"))
                .andExpect(jsonPath("$[0].correoDestino").value("cobranzas@costaazul.test"))
                .andExpect(jsonPath("$[0].idObligacion").value("OBL-8001"))
                .andExpect(jsonPath("$[0].estado").value("ENVIADO"));

        // Consultar historial por endpoint de alertas (/api/alertas-mora/contribuyentes/021390)
        mockMvc.perform(get(RUTA_ALERTAS + "/contribuyentes/021390"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idObligacion").value("OBL-8001"));
    }

    @Test
    void contribuyenteSinCorreoRegistradoEsExcluidoYNoSeUsanWhatsappSmsNiIvr() throws Exception {
        LoteETL lote = lotePublicado();

        // 033218 es un contribuyente sin correo registrado en la BD sintética
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-9001",
                            "contribuyente_codigo": "033218",
                            "fecha_vencimiento": "2026-09-30",
                            "insoluto": 80.00
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotasEvaluadas").value(1))
                .andExpect(jsonPath("$.cuotasVencidasDetectadas").value(1))
                .andExpect(jsonPath("$.alertasEnviadas").value(0))
                .andExpect(jsonPath("$.sinCorreoRegistrado").value(1))
                .andExpect(jsonPath("$.avisos", hasSize(0)));

        // No se generó aviso por correo para 033218
        mockMvc.perform(get("/api/contribuyentes/033218/avisos-mora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void loteRechazadoPorCalidadNoPuedeGenerarAlertasDeMoraTemprana() throws Exception {
        // Simular lote rechazado por reglas de calidad de HU026
        LoteETL loteRechazado = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", FECHA_CORTE,
                TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0)));
        loteRechazado.rechazar(10, 8, LocalDateTime.now());
        lotes.saveAndFlush(loteRechazado);

        String json = """
                {
                    "idLote": %d
                }
                """.formatted(loteRechazado.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LOTE_NO_PUBLICADO"));
    }

    @Test
    void cuotasNoVencidasOConSaldoCeroNoDisparanAlertas() throws Exception {
        LoteETL lote = lotePublicado();

        // Cuota 1: vence en el futuro (2026-11-30 > 2026-10-19)
        // Cuota 2: vencida pero con saldo insoluto 0 (ya pagada)
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-FUTURA",
                            "contribuyente_codigo": "014502",
                            "fecha_vencimiento": "2026-11-30",
                            "insoluto": 200.00
                        },
                        {
                            "id_obligacion": "OBL-PAGADA",
                            "contribuyente_codigo": "014502",
                            "fecha_vencimiento": "2026-08-31",
                            "insoluto": 0.00
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotasEvaluadas").value(2))
                .andExpect(jsonPath("$.cuotasVencidasDetectadas").value(0))
                .andExpect(jsonPath("$.alertasEnviadas").value(0));
    }

    @Test
    void noGeneraAvisosDuplicadosParaLaMismaObligacionEnElMismoLote() throws Exception {
        LoteETL lote = lotePublicado();

        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-UNICA",
                            "contribuyente_codigo": "014502",
                            "fecha_vencimiento": "2026-09-30",
                            "insoluto": 100.00
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        // Primer procesamiento: envía la alerta
        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasEnviadas").value(1));

        // Segundo procesamiento idéntico: no duplica el aviso
        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasEnviadas").value(0));
    }

    @Test
    void consultarAvisosPorLoteEIdDeAviso() throws Exception {
        LoteETL lote = lotePublicado();

        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "id_obligacion": "OBL-CONSULTA",
                            "contribuyente_codigo": "014502",
                            "fecha_vencimiento": "2026-09-30",
                            "insoluto": 120.00
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        // Consultar por lote
        mockMvc.perform(get(RUTA_ALERTAS + "/lotes/" + lote.getIdLote()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idObligacion").value("OBL-CONSULTA"));

        Long idAviso = avisos.findByLoteIdLoteOrderByFechaProgramadaDesc(lote.getIdLote()).getFirst().getIdAviso();

        // Consultar por ID
        mockMvc.perform(get(RUTA_ALERTAS + "/" + idAviso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAviso").value(idAviso))
                .andExpect(jsonPath("$.correoDestino").value("maria.garcia@correo.test"));
    }

    private LoteETL lotePublicado() {
        LoteETL lote = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", FECHA_CORTE,
                TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0)));
        lote.publicar(10, 10, FECHA_CORTE.atTime(2, 5));
        return lotes.saveAndFlush(lote);
    }
}

