package pe.municipalidad.tributario.indicador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU008: índice de morosidad. Muestra de control calculada a mano, con fecha de corte 2026-10-19:
 *
 *   Zona  Tributo            Vence       Emitido  Insoluto  Estado     ¿Exigible?
 *   Z01   IMPUESTO_PREDIAL   2026-02-27    400       0      PAGADA     sí
 *   Z01   IMPUESTO_PREDIAL   2026-05-29    400     400      VENCIDA    sí
 *   Z01   ARBITRIOS          2026-09-30     50      20      VENCIDA    sí (pago parcial de 30)
 *   Z02   ARBITRIOS          2026-09-30     50       0      PAGADA     sí
 *   Z02   ARBITRIOS          2026-10-19     30      30      PENDIENTE  no: vence el mismo día de corte
 *   Z02   IMPUESTO_PREDIAL   2026-11-30    400     400      PENDIENTE  no: aún no vence
 *   Z02   ARBITRIOS          2025-12-31     50      50      VENCIDA    no: es de otro año
 *
 *   Total             420 / 900 x 100 = 46,6667
 *   IMPUESTO_PREDIAL  400 / 800 x 100 = 50,0000
 *   ARBITRIOS          20 / 100 x 100 = 20,0000
 *   Z01               420 / 850 x 100 = 49,4118
 *   Z02                 0 /  50 x 100 =  0,0000
 */
@PruebaIntegracion
@Transactional
class IndiceMorosidadControllerTest {

    private static final String RUTA = "/api/indicadores/morosidad";
    private static final String RUTA_CALCULOS = RUTA + "/calculos";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void calculaElIndiceTotalPorTributoYPorZonaComoLaMuestraDeControl() throws Exception {
        LoteETL lote = loteConMuestraDeControl(true);

        calcular(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.total.montoEmitido").value(900.0))
                .andExpect(jsonPath("$.total.montoVencidoNoPagado").value(420.0))
                .andExpect(jsonPath("$.total.indice").value(46.6667))
                .andExpect(jsonPath("$.porTributo", hasSize(2)))
                .andExpect(jsonPath("$.porTributo[0].valorAlcance").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.porTributo[0].indice").value(50.0))
                .andExpect(jsonPath("$.porTributo[1].valorAlcance").value("ARBITRIOS"))
                .andExpect(jsonPath("$.porTributo[1].indice").value(20.0))
                .andExpect(jsonPath("$.porZona", hasSize(2)))
                .andExpect(jsonPath("$.porZona[0].valorAlcance").value("Z01"))
                .andExpect(jsonPath("$.porZona[0].indice").value(49.4118))
                .andExpect(jsonPath("$.porZona[1].valorAlcance").value("Z02"))
                .andExpect(jsonPath("$.porZona[1].indice").value(0.0));

        assertThat(valoresGuardados(lote)).isEqualTo(5);
    }

    @Test
    void elDashboardConsultaElIndiceDeLaUltimaCargaPublicada() throws Exception {
        calcular(loteConMuestraDeControl(true)).andExpect(status().isOk());

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.total").value(46.6667))
                .andExpect(jsonPath("$.porTributo", hasSize(2)))
                .andExpect(jsonPath("$.porZona[0].valorAlcance").value("Z01"))
                .andExpect(jsonPath("$.porZona[0].indice").value(49.4118));
    }

    @Test
    void recalcularElMismoLoteActualizaLosValoresSinDuplicarlos() throws Exception {
        LoteETL lote = loteConMuestraDeControl(true);
        calcular(lote);
        jdbc.update("UPDATE analitica.hecho_cobranza SET monto_insoluto = 0, estado_pago = 'PAGADA' "
                + "WHERE id_lote = ? AND tributo = 'ARBITRIOS'", lote.getIdLote());

        calcular(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.indice").value(44.4444))
                .andExpect(jsonPath("$.porTributo[1].indice").value(0.0));
        assertThat(valoresGuardados(lote)).isEqualTo(5);
    }

    @Test
    void enUnLotePublicadoElIndiceSeComparaConSuMetaYGeneraLaAlerta() throws Exception {
        calcular(loteConMuestraDeControl(true))
                .andExpect(jsonPath("$.evaluacionAlertas.alertasActivas", hasSize(1)))
                .andExpect(jsonPath("$.evaluacionAlertas.alertasActivas[0].indicadorCodigo")
                        .value(IndiceMorosidadService.CODIGO_INDICADOR))
                .andExpect(jsonPath("$.evaluacionAlertas.alertasActivas[0].desvioPorcentual").value(273.33));
    }

    @Test
    void enUnLoteEnCursoSeCalculaPeroLasAlertasEsperanASuPublicacion() throws Exception {
        calcular(loteConMuestraDeControl(false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.indice").value(46.6667))
                .andExpect(jsonPath("$.evaluacionAlertas").value(nullValue()));
    }

    @Test
    void unLoteRechazadoNoSeCalcula() throws Exception {
        LoteETL lote = new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0));
        lote.rechazar(1, 0, FECHA_CORTE.atTime(2, 5));
        lotes.saveAndFlush(lote);

        calcular(lote)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("LOTE_RECHAZADO"));
    }

    @Test
    void unLoteSinCuotasExigiblesNoSeCalcula() throws Exception {
        LoteETL lote = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA,
                FECHA_CORTE.atTime(2, 0)));

        calcular(lote)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_CUOTAS_EXIGIBLES"));
    }

    @Test
    void loteInexistenteOPeriodoInvalido() throws Exception {
        mockMvc.perform(post(RUTA_CALCULOS).contentType(MediaType.APPLICATION_JSON).content("{\"idLote\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("LOTE_NO_ENCONTRADO"));

        mockMvc.perform(get(RUTA).param("numeroPeriodo", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value("numeroPeriodo: debe estar entre 1 y 12"));

        mockMvc.perform(get(RUTA).param("anio", "2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("MOROSIDAD_NO_CALCULADA"));
    }

    private LoteETL loteConMuestraDeControl(boolean publicado) {
        LoteETL lote = new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0));
        if (publicado) {
            lote.publicar(7, 7, FECHA_CORTE.atTime(2, 5));
        }
        lotes.saveAndFlush(lote);

        jdbc.update("INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona) "
                + "VALUES ('SEUD-CONTROL-HU008', 'NATURAL') ON CONFLICT DO NOTHING");
        long zona1 = predio("SEUD-PREDIO-HU008-1", "Z01");
        long zona2 = predio("SEUD-PREDIO-HU008-2", "Z02");

        cuota(lote, zona1, "IMPUESTO_PREDIAL", 2026, 1, "2026-02-27", "400", "0", "PAGADA");
        cuota(lote, zona1, "IMPUESTO_PREDIAL", 2026, 2, "2026-05-29", "400", "400", "VENCIDA");
        cuota(lote, zona1, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "20", "VENCIDA");
        cuota(lote, zona2, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "0", "PAGADA");
        cuota(lote, zona2, "ARBITRIOS", 2026, 10, "2026-10-19", "30", "30", "PENDIENTE");
        cuota(lote, zona2, "IMPUESTO_PREDIAL", 2026, 4, "2026-11-30", "400", "400", "PENDIENTE");
        cuota(lote, zona2, "ARBITRIOS", 2025, 12, "2025-12-31", "50", "50", "VENCIDA");
        return lote;
    }

    private long predio(String seudonimo, String zona) {
        return jdbc.queryForObject("INSERT INTO analitica.dim_predio (seudonimo_predio, zona, tipo_predio) "
                + "VALUES (?, ?, 'URBANO') RETURNING id_predio", Long.class, seudonimo, zona);
    }

    private void cuota(LoteETL lote, long idPredio, String tributo, int anio, int cuota, String vence,
            String emitido, String insoluto, String estado) {
        BigDecimal montoEmitido = new BigDecimal(emitido);
        BigDecimal montoInsoluto = new BigDecimal(insoluto);
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza
                    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
                     monto_emitido, monto_insoluto, monto_pagado, estado_pago)
                VALUES (?, 'SEUD-CONTROL-HU008', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, lote.getIdLote(), idPredio, tributo, anio, cuota, LocalDate.parse(vence), montoEmitido,
                montoInsoluto, montoEmitido.subtract(montoInsoluto), estado);
    }

    private ResultActions calcular(LoteETL lote) throws Exception {
        return mockMvc.perform(post(RUTA_CALCULOS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLote\": " + lote.getIdLote() + "}"));
    }

    private Integer valoresGuardados(LoteETL lote) {
        return jdbc.queryForObject("SELECT count(*) FROM analitica.valor_indicador "
                + "WHERE id_lote = ? AND indicador_codigo = 'INDICE_MOROSIDAD'", Integer.class, lote.getIdLote());
    }
}
