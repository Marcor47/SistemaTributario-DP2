package pe.municipalidad.tributario.dashboard;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU011: dashboard ejecutivo. Muestra de control con fecha de corte 2026-10-19 (la misma de HU008, con lo pagado):
 *
 *   Contrib.  Segmento     Zona  Tributo           Vence       Emitido  Pagado  Insoluto  ¿Exigible?
 *   C1        Alto riesgo  Z01   IMPUESTO_PREDIAL  2026-02-27    400     400       0      sí
 *   C1        Alto riesgo  Z01   IMPUESTO_PREDIAL  2026-05-29    400       0     400      sí
 *   C1        Alto riesgo  Z01   ARBITRIOS         2026-09-30     50      30      20      sí
 *   C2        Puntual      Z02   ARBITRIOS         2026-09-30     50      50       0      sí
 *   C2        Puntual      Z02   ARBITRIOS         2026-10-19     30       0      30      no: vence el día de corte
 *   C2        Puntual      Z02   IMPUESTO_PREDIAL  2026-11-30    400       0     400      no: aún no vence
 *   C2        Puntual      Z02   ARBITRIOS         2025-12-31     50       0      50      no: es de otro año
 *
 *   Total: emitido 900, recaudado 480 (53,3333 %), vencido sin pagar 420 (morosidad 46,6667 %)
 *   IMPUESTO_PREDIAL 400/800 = 50 %, participación 400/480 = 83,3333 %; ARBITRIOS 80/100 = 80 %, 16,6667 %
 *   Z01 430/850 = 50,5882 %, morosidad 49,4118 %; Z02 50/50 = 100 %, morosidad 0
 */
@PruebaInterna
@Transactional
class DashboardControllerTest {

    private static final String RUTA = "/api/dashboard";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private JdbcTemplate jdbc;

    private long segmentoRiesgo;
    private long segmentoPuntual;

    @BeforeEach
    void cargarMuestraDeControl() {
        LoteETL lote = new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0));
        lote.publicar(7, 7, FECHA_CORTE.atTime(2, 5));
        lotes.saveAndFlush(lote);

        contribuyente("SEUD-HU011-C1");
        contribuyente("SEUD-HU011-C2");
        long z01 = predio("SEUD-PREDIO-HU011-1", "Z01");
        long z02 = predio("SEUD-PREDIO-HU011-2", "Z02");
        cuota(lote, "SEUD-HU011-C1", z01, "IMPUESTO_PREDIAL", 2026, 1, "2026-02-27", "400", "0", "PAGADA");
        cuota(lote, "SEUD-HU011-C1", z01, "IMPUESTO_PREDIAL", 2026, 2, "2026-05-29", "400", "400", "VENCIDA");
        cuota(lote, "SEUD-HU011-C1", z01, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "20", "VENCIDA");
        cuota(lote, "SEUD-HU011-C2", z02, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "0", "PAGADA");
        cuota(lote, "SEUD-HU011-C2", z02, "ARBITRIOS", 2026, 10, "2026-10-19", "30", "30", "PENDIENTE");
        cuota(lote, "SEUD-HU011-C2", z02, "IMPUESTO_PREDIAL", 2026, 4, "2026-11-30", "400", "400", "PENDIENTE");
        cuota(lote, "SEUD-HU011-C2", z02, "ARBITRIOS", 2025, 12, "2025-12-31", "50", "50", "VENCIDA");

        long modelo = jdbc.queryForObject("""
                INSERT INTO analitica.modelo_ia (nombre, tipo, version, hash_integridad, formato, fecha_registro)
                VALUES ('kmeans-hu011', 'SEGMENTACION', '1.0', repeat('b', 64), 'ONNX', TIMESTAMP '2026-10-01 08:00')
                RETURNING id_modelo
                """, Long.class);
        segmentoRiesgo = segmento(modelo, "Alto riesgo", "SEUD-HU011-C1");
        segmentoPuntual = segmento(modelo, "Puntual", "SEUD-HU011-C2");

        meta("PORC_RECAUDACION_EMISION", "85.00");
        meta("INDICE_MOROSIDAD", "12.50");
        meta("MONTO_RECAUDADO", "400.00");
    }

    @Test
    void sinFiltrosMuestraLosIndicadoresDeLaUltimaCarga() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorteCarga").value("2026-10-19"))
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.resumen.montoEmitido").value(900.0))
                .andExpect(jsonPath("$.resumen.montoRecaudado").value(480.0))
                .andExpect(jsonPath("$.resumen.porcentajeRecaudacion").value(53.3333))
                .andExpect(jsonPath("$.resumen.montoVencidoNoPagado").value(420.0))
                .andExpect(jsonPath("$.resumen.indiceMorosidad").value(46.6667))
                .andExpect(jsonPath("$.porTributo", hasSize(2)))
                .andExpect(jsonPath("$.porTributo[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.porTributo[0].porcentajeRecaudacion").value(50.0))
                .andExpect(jsonPath("$.porTributo[0].participacionRecaudacion").value(83.3333))
                .andExpect(jsonPath("$.porTributo[1].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.porTributo[1].porcentajeRecaudacion").value(80.0))
                .andExpect(jsonPath("$.porTributo[1].indiceMorosidad").value(20.0))
                .andExpect(jsonPath("$.porZona[0].zona").value("Z01"))
                .andExpect(jsonPath("$.porZona[0].porcentajeRecaudacion").value(50.5882))
                .andExpect(jsonPath("$.porZona[0].indiceMorosidad").value(49.4118))
                .andExpect(jsonPath("$.porZona[1].indiceMorosidad").value(0.0))
                .andExpect(jsonPath("$.porSegmento", hasSize(2)))
                .andExpect(jsonPath("$.porSegmento[0].nombre").value("Alto riesgo"))
                .andExpect(jsonPath("$.porSegmento[0].contribuyentes").value(1))
                .andExpect(jsonPath("$.porSegmento[0].montoEmitido").value(850.0))
                .andExpect(jsonPath("$.porSegmento[1].nombre").value("Puntual"))
                .andExpect(jsonPath("$.porSegmento[1].porcentajeRecaudacion").value(100.0));
    }

    @Test
    void comparaCadaIndicadorConSuMetaDelPeriodo() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(jsonPath("$.avanceMetas", hasSize(3)))
                .andExpect(jsonPath("$.avanceMetas[0].indicadorCodigo").value("PORC_RECAUDACION_EMISION"))
                .andExpect(jsonPath("$.avanceMetas[0].valorObservado").value(53.3333))
                .andExpect(jsonPath("$.avanceMetas[0].porcentajeAvance").value(62.7451))
                .andExpect(jsonPath("$.avanceMetas[0].cumpleMeta").value(false))
                .andExpect(jsonPath("$.avanceMetas[1].indicadorCodigo").value("INDICE_MOROSIDAD"))
                .andExpect(jsonPath("$.avanceMetas[1].sentido").value("DESCENDENTE"))
                .andExpect(jsonPath("$.avanceMetas[1].cumpleMeta").value(false))
                .andExpect(jsonPath("$.avanceMetas[2].indicadorCodigo").value("MONTO_RECAUDADO"))
                .andExpect(jsonPath("$.avanceMetas[2].porcentajeAvance").value(120.0))
                .andExpect(jsonPath("$.avanceMetas[2].cumpleMeta").value(true));
    }

    @Test
    void losFiltrosActualizanLosIndicadores() throws Exception {
        mockMvc.perform(get(RUTA).param("idSegmento", String.valueOf(segmentoPuntual)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.segmento").value("Puntual"))
                .andExpect(jsonPath("$.resumen.montoEmitido").value(50.0))
                .andExpect(jsonPath("$.resumen.indiceMorosidad").value(0.0))
                .andExpect(jsonPath("$.porSegmento", hasSize(1)))
                .andExpect(jsonPath("$.avanceMetas", hasSize(0)));

        mockMvc.perform(get(RUTA).param("zona", "Z01").param("tributo", "ARBITRIOS"))
                .andExpect(jsonPath("$.resumen.montoEmitido").value(50.0))
                .andExpect(jsonPath("$.resumen.porcentajeRecaudacion").value(60.0))
                .andExpect(jsonPath("$.resumen.indiceMorosidad").value(40.0))
                .andExpect(jsonPath("$.avanceMetas", hasSize(0)));

        mockMvc.perform(get(RUTA).param("anio", "2026").param("numeroPeriodo", "6"))
                .andExpect(jsonPath("$.periodo").value("2026-06"))
                .andExpect(jsonPath("$.fechaCorte").value("2026-07-01"))
                .andExpect(jsonPath("$.resumen.montoEmitido").value(800.0))
                .andExpect(jsonPath("$.resumen.porcentajeRecaudacion").value(50.0))
                .andExpect(jsonPath("$.porTributo", hasSize(1)));
    }

    @Test
    void laMetaDeUnSegmentoSoloSeComparaAlFiltrarPorEse() throws Exception {
        jdbc.update("UPDATE analitica.meta_indicador SET alcance = 'SEGMENTO', valor_alcance = 'Alto riesgo' "
                + "WHERE indicador_codigo = 'INDICE_MOROSIDAD' AND anio = 2026 AND numero_periodo = 10");

        mockMvc.perform(get(RUTA))
                .andExpect(jsonPath("$.avanceMetas[*].indicadorCodigo",
                        org.hamcrest.Matchers.not(hasItem("INDICE_MOROSIDAD"))));
        mockMvc.perform(get(RUTA).param("idSegmento", String.valueOf(segmentoRiesgo)))
                .andExpect(jsonPath("$.avanceMetas", hasSize(1)))
                .andExpect(jsonPath("$.avanceMetas[0].alcance").value("SEGMENTO"))
                .andExpect(jsonPath("$.avanceMetas[0].valorObservado").value(49.4118));
    }

    @Test
    void ofreceLosFiltrosDisponibles() throws Exception {
        mockMvc.perform(get(RUTA + "/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas[0]").value("Z01"))
                .andExpect(jsonPath("$.anios[0]").value(2026))
                .andExpect(jsonPath("$.tributos[1].codigo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.segmentos", hasSize(2)));
    }

    @Test
    void validaElSegmentoYElPeriodoYExigePermiso() throws Exception {
        jdbc.update("UPDATE analitica.segmento SET estado = 'RETIRADO' WHERE id_segmento = ?", segmentoPuntual);

        mockMvc.perform(get(RUTA).param("idSegmento", String.valueOf(segmentoPuntual)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTO_NO_PUBLICADO"));
        mockMvc.perform(get(RUTA).param("numeroPeriodo", "13"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).param("tributo", "ALCABALA"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).with(user("analista")
                        .authorities(new SimpleGrantedAuthority("INDICADORES:CONSULTAR"))))
                .andExpect(status().isForbidden());
    }

    private void contribuyente(String seudonimo) {
        jdbc.update("INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona) "
                + "VALUES (?, 'NATURAL') ON CONFLICT DO NOTHING", seudonimo);
    }

    private long predio(String seudonimo, String zona) {
        return jdbc.queryForObject("INSERT INTO analitica.dim_predio (seudonimo_predio, zona, tipo_predio) "
                + "VALUES (?, ?, 'URBANO') RETURNING id_predio", Long.class, seudonimo, zona);
    }

    private void cuota(LoteETL lote, String contribuyente, long idPredio, String tributo, int anio, int cuota,
            String vence, String emitido, String insoluto, String estado) {
        BigDecimal montoEmitido = new BigDecimal(emitido);
        BigDecimal montoInsoluto = new BigDecimal(insoluto);
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza
                    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
                     monto_emitido, monto_insoluto, monto_pagado, estado_pago)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, lote.getIdLote(), contribuyente, idPredio, tributo, anio, cuota, LocalDate.parse(vence),
                montoEmitido, montoInsoluto, montoEmitido.subtract(montoInsoluto), estado);
    }

    private long segmento(long idModelo, String nombre, String seudonimo) {
        long id = jdbc.queryForObject("""
                INSERT INTO analitica.segmento
                    (id_modelo, nombre, tamano, variables_caracteristicas, estado, id_aprobador, fecha_aprobacion)
                SELECT ?, ?, 1, '{}', 'PUBLICADO', id_usuario, TIMESTAMP '2026-10-15 09:00'
                FROM seguridad.usuario WHERE nombre_usuario = 'gerencia'
                RETURNING id_segmento
                """, Long.class, idModelo, nombre);
        jdbc.update("INSERT INTO analitica.asignacion_segmento (id_segmento, seudonimo_contribuyente, fecha_asignacion) "
                + "VALUES (?, ?, TIMESTAMP '2026-10-15 09:00')", id, seudonimo);
        return id;
    }

    private void meta(String indicador, String valor) {
        jdbc.update("""
                INSERT INTO analitica.meta_indicador
                    (indicador_codigo, anio, numero_periodo, valor_meta, fecha_registro, fecha_actualizacion)
                VALUES (?, 2026, 10, ?, TIMESTAMP '2026-09-20 10:00', TIMESTAMP '2026-09-20 10:00')
                ON CONFLICT (indicador_codigo, anio, numero_periodo)
                    DO UPDATE SET valor_meta = EXCLUDED.valor_meta, alcance = 'TOTAL', valor_alcance = NULL
                """, indicador, new BigDecimal(valor));
    }
}
