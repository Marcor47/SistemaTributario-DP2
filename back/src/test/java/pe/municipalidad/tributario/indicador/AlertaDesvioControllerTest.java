package pe.municipalidad.tributario.indicador;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU039: alerta de desvío de meta. Los datos de prueba dejan, con la carga del 2026-10-18, dos alertas activas
 * de octubre de 2026: índice de morosidad 14,80 % frente a la meta de 12,50 % (+18,40 %) y recaudación sobre
 * la emisión 72 % frente a 85 % (-15,29 %). Cada test crea sus propias cargas y se revierte al terminar.
 */
@PruebaInterna
@Transactional
class AlertaDesvioControllerTest {

    private static final String RUTA_ALERTAS = "/api/alertas-desvio";
    private static final String RUTA_EVALUACIONES = RUTA_ALERTAS + "/evaluaciones";
    private static final String MOROSIDAD = "INDICE_MOROSIDAD";
    private static final String RECAUDACION = "PORC_RECAUDACION_EMISION";
    private static final String RECUPERACION = "RECUPERACION_DEUDA";
    private static final String MONTO_RECAUDADO = "MONTO_RECAUDADO";
    private static final int OCTUBRE = 10;
    private static final int NOVIEMBRE = 11;
    private static final int CUARTO_TRIMESTRE = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private ValorIndicadorRepository valores;

    @Autowired
    private IndicadorRepository indicadores;

    @Test
    void elDashboardMuestraLasAlertasActivasConIndicadorValorMetaYDesvio() throws Exception {
        mockMvc.perform(get(RUTA_ALERTAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].indicadorNombre").isNotEmpty())
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].valorObservado").value(14.8))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].valorMeta").value(12.5))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].desvioPorcentual").value(18.4))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].umbralDesvio").value(10.0))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].periodo").value("2026-10"))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'INDICE_MOROSIDAD')].fechaCorte").value("2026-10-18"))
                .andExpect(jsonPath("$[?(@.indicadorCodigo == 'PORC_RECAUDACION_EMISION')].desvioPorcentual")
                        .value(-15.29));

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].indicadorCodigo").value(MOROSIDAD));
    }

    @Test
    void laNuevaCargaReemplazaLaAlertaYResuelveLaQueVolvioDentroDelUmbral() throws Exception {
        LoteETL lote = cargaPublicada(LocalDate.of(2026, 10, 19), Map.of(
                MOROSIDAD, new BigDecimal("15.0000"),
                RECAUDACION, new BigDecimal("80.0000"),
                MONTO_RECAUDADO, new BigDecimal("950000.0000")));
        valor(lote, RECUPERACION, CUARTO_TRIMESTRE, new BigDecimal("33.0000"));

        evaluar(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.valoresEvaluados").value(4))
                .andExpect(jsonPath("$.valoresSinMeta").value(1))
                .andExpect(jsonPath("$.alertasGeneradas").value(0))
                .andExpect(jsonPath("$.alertasResueltas").value(1))
                .andExpect(jsonPath("$.alertasActivas", hasSize(1)))
                .andExpect(jsonPath("$.alertasActivas[0].indicadorCodigo").value(MOROSIDAD))
                .andExpect(jsonPath("$.alertasActivas[0].desvioPorcentual").value(20.0));

        mockMvc.perform(get(RUTA_ALERTAS))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].indicadorCodigo").value(MOROSIDAD))
                .andExpect(jsonPath("$[0].valorObservado").value(15.0))
                .andExpect(jsonPath("$[0].idLote").value(lote.getIdLote()));
    }

    @Test
    void laAlertaDesapareceCuandoElIndicadorVuelveDentroDelUmbral() throws Exception {
        LoteETL lote = cargaPublicada(LocalDate.of(2026, 10, 19), Map.of(MOROSIDAD, new BigDecimal("13.0000")));

        evaluar(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasResueltas").value(1))
                .andExpect(jsonPath("$.alertasActivas", empty()));

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void reevaluarLaMismaCargaNoDuplicaLaAlerta() throws Exception {
        LoteETL lote = cargaPublicada(LocalDate.of(2026, 10, 19), Map.of(MOROSIDAD, new BigDecimal("16.0000")));

        evaluar(lote).andExpect(jsonPath("$.alertasActivas", hasSize(1)));
        evaluar(lote)
                .andExpect(jsonPath("$.alertasGeneradas").value(0))
                .andExpect(jsonPath("$.alertasResueltas").value(0))
                .andExpect(jsonPath("$.alertasActivas", hasSize(1)));

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].valorObservado").value(16.0));
    }

    @Test
    void unaCargaAntiguaEvaluadaTardeNoCambiaLaAlerta() throws Exception {
        LoteETL reciente = cargaPublicada(LocalDate.of(2026, 10, 19), Map.of(MOROSIDAD, new BigDecimal("13.0000")));
        evaluar(reciente).andExpect(jsonPath("$.alertasResueltas").value(1));

        LoteETL antigua = cargaPublicada(LocalDate.of(2026, 10, 10), Map.of(MOROSIDAD, new BigDecimal("20.0000")));
        evaluar(antigua)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasGeneradas").value(0))
                .andExpect(jsonPath("$.alertasActivas", empty()));

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void cambiarElUmbralDeLaMetaRecalculaLaAlerta() throws Exception {
        long idMeta = idMetaDeMorosidadDeOctubre();

        actualizarMeta(idMeta, "{\"valorMeta\": 12.50, \"umbralDesvio\": 25}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralDesvio").value(25));
        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", empty()));

        actualizarMeta(idMeta, "{\"valorMeta\": 12.50, \"umbralDesvio\": 5}")
                .andExpect(jsonPath("$.umbralDesvio").value(5));
        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].umbralDesvio").value(5.0))
                .andExpect(jsonPath("$[0].desvioPorcentual").value(18.4));
    }

    @Test
    void sinEnviarElUmbralLaMetaConservaElActual() throws Exception {
        long idMeta = idMetaDeMorosidadDeOctubre();
        actualizarMeta(idMeta, "{\"valorMeta\": 12.50, \"umbralDesvio\": 30}");

        actualizarMeta(idMeta, "{\"valorMeta\": 12.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralDesvio").value(30.0));
    }

    @Test
    void laMetaRegistradaDespuesDeLaCargaSeEvaluaConElUmbralPorDefecto() throws Exception {
        LoteETL lote = cargaPublicada(LocalDate.of(2026, 10, 19), Map.of());
        valor(lote, MONTO_RECAUDADO, NOVIEMBRE, new BigDecimal("850000.0000"));

        mockMvc.perform(post("/api/metas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"indicadorCodigo": "MONTO_RECAUDADO", "anio": 2026, "numeroPeriodo": 11,
                         "valorMeta": 1000000}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.umbralDesvio").value(10.0));

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MONTO_RECAUDADO))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].desvioPorcentual").value(-15.0))
                .andExpect(jsonPath("$[0].periodo").value("2026-11"));
    }

    @Test
    void umbralFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/metas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"indicadorCodigo": "MONTO_RECAUDADO", "anio": 2026, "numeroPeriodo": 12,
                         "valorMeta": 1000000, "umbralDesvio": 150}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value("umbralDesvio: no puede superar 100"));
    }

    @Test
    void eliminarUnaMetaConAlertaTambienRetiraSuAlerta() throws Exception {
        mockMvc.perform(delete("/api/metas/" + idMetaDeMorosidadDeOctubre()).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RUTA_ALERTAS).param("indicadorCodigo", MOROSIDAD))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void soloSeEvaluanLotesPublicados() throws Exception {
        LoteETL enCurso = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", LocalDate.of(2026, 10, 19),
                TipoEjecucion.A_DEMANDA, LocalDate.of(2026, 10, 19).atTime(2, 0)));

        evaluar(enCurso)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("LOTE_NO_PUBLICADO"));
    }

    @Test
    void loteInexistenteDevuelve404YSinLoteDevuelve400() throws Exception {
        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLote\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("LOTE_NO_ENCONTRADO"));

        mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value("idLote: es obligatorio"));
    }

    /* Carga publicada con valores mensuales de octubre de 2026 y alcance total. */
    private LoteETL cargaPublicada(LocalDate fechaCorte, Map<String, BigDecimal> valoresDeOctubre) {
        LoteETL lote = new LoteETL("ps_etl_cobranza", fechaCorte, TipoEjecucion.PROGRAMADA, fechaCorte.atTime(2, 0));
        lote.publicar(1, 1, fechaCorte.atTime(2, 5));
        lotes.saveAndFlush(lote);
        valoresDeOctubre.forEach((codigo, valor) -> valor(lote, codigo, OCTUBRE, valor));
        return lote;
    }

    private void valor(LoteETL lote, String indicadorCodigo, int numeroPeriodo, BigDecimal valor) {
        valores.saveAndFlush(new ValorIndicador(indicadores.findById(indicadorCodigo).orElseThrow(), lote, 2026,
                numeroPeriodo, AlcanceIndicador.TOTAL, null, valor));
    }

    private ResultActions evaluar(LoteETL lote) throws Exception {
        return mockMvc.perform(post(RUTA_EVALUACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLote\": " + lote.getIdLote() + "}"));
    }

    private ResultActions actualizarMeta(long idMeta, String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/metas/" + idMeta).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private long idMetaDeMorosidadDeOctubre() throws Exception {
        String metas = mockMvc.perform(get("/api/metas").param("indicadorCodigo", MOROSIDAD).param("anio", "2026"))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(metas, "$[?(@.numeroPeriodo == 10)].id");
        return ids.getFirst().longValue();
    }
}
