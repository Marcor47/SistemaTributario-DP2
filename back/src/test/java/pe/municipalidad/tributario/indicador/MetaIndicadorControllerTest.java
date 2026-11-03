package pe.municipalidad.tributario.indicador;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU009: configurar una meta por indicador. La fecha de los tests es 2026-10-19,
 * así que octubre de 2026 sigue abierto y septiembre de 2026 ya concluyó.
 * Cada test se revierte al terminar para no ensuciar los datos de los demás.
 */
@PruebaInterna
@Transactional
class MetaIndicadorControllerTest {

    private static final String RUTA_METAS = "/api/metas";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogoListaSoloIndicadoresActivosConSuFicha() throws Exception {
        mockMvc.perform(get("/api/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("CUPONERAS_ENTREGADAS"))))
                .andExpect(jsonPath("$[?(@.codigo == 'INDICE_MOROSIDAD')].sentido").value("DESCENDENTE"))
                .andExpect(jsonPath("$[?(@.codigo == 'INDICE_MOROSIDAD')].periodosPorAnio").value(12))
                .andExpect(jsonPath("$[?(@.codigo == 'RECUPERACION_DEUDA')].periodicidad").value("TRIMESTRAL"))
                .andExpect(jsonPath("$[0].formula").isNotEmpty())
                .andExpect(jsonPath("$[0].fuente").isNotEmpty());
    }

    @Test
    void registraMetaMensualYDevuelve201ConLaUbicacion() throws Exception {
        String cuerpo = """
                {"indicadorCodigo": "MONTO_RECAUDADO", "anio": 2026, "numeroPeriodo": 11,
                 "valorMeta": 1250000.50, "observacion": "  Campaña de noviembre  "}
                """;
        registrar(cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/metas/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.indicadorNombre").value("Monto recaudado"))
                .andExpect(jsonPath("$.unidadMedida").value("SOLES"))
                .andExpect(jsonPath("$.periodo").value("2026-11"))
                .andExpect(jsonPath("$.fechaFinPeriodo").value("2026-11-30"))
                .andExpect(jsonPath("$.valorMeta").value(1250000.50))
                .andExpect(jsonPath("$.observacion").value("Campaña de noviembre"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.fechaRegistro").value("2026-10-19T12:00:00"));
    }

    @Test
    void registraMetaDelMesEnCursoPorqueAunNoConcluye() throws Exception {
        registrar(cuerpoMeta("MONTO_RECAUDADO", 2026, 10, "900000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-10"));
    }

    @Test
    void registraMetaTrimestralYAnual() throws Exception {
        registrar(cuerpoMeta("INDICE_MOROSIDAD", 2027, 1, "11"))
                .andExpect(status().isCreated());
        registrar(cuerpoMeta("ACTUALIZACION_PADRON", 2026, 1, "60"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026"))
                .andExpect(jsonPath("$.fechaFinPeriodo").value("2026-12-31"));
        registrar(cuerpoMeta("RECUPERACION_DEUDA", 2027, 2, "32.5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2027-T2"))
                .andExpect(jsonPath("$.fechaFinPeriodo").value("2027-06-30"));
    }

    @Test
    void metaRepetidaParaElMismoPeriodoDevuelve409() throws Exception {
        registrar(cuerpoMeta("PORC_RECAUDACION_EMISION", 2026, 10, "80"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("META_DUPLICADA"));
    }

    @Test
    void periodoConcluidoDevuelve422() throws Exception {
        registrar(cuerpoMeta("MONTO_RECAUDADO", 2026, 9, "800000"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_CERRADO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "El periodo 2026-09 ya concluyó; su meta no puede registrarse ni modificarse."));
    }

    @Test
    void numeroDePeriodoFueraDeLaPeriodicidadDevuelve400() throws Exception {
        registrar(cuerpoMeta("RECUPERACION_DEUDA", 2027, 5, "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
        registrar(cuerpoMeta("ACTUALIZACION_PADRON", 2027, 2, "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void porcentajeMayorACienDevuelve400() throws Exception {
        registrar(cuerpoMeta("INDICE_MOROSIDAD", 2026, 12, "100.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALOR_META_FUERA_DE_RANGO"));
    }

    @Test
    void indicadorInexistenteDevuelve404EIndicadorInactivoDevuelve422() throws Exception {
        registrar(cuerpoMeta("NO_EXISTE", 2026, 12, "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("INDICADOR_NO_ENCONTRADO"));
        registrar(cuerpoMeta("CUPONERAS_ENTREGADAS", 2026, 1, "100"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("INDICADOR_INACTIVO"));
    }

    @Test
    void cuerpoConCamposFaltantesONegativosDevuelve400ConDetalle() throws Exception {
        registrar("""
                {"indicadorCodigo": "", "anio": 1999, "valorMeta": -5}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles", hasSize(4)))
                .andExpect(jsonPath("$.detalles", hasItem("anio: debe ser 2000 o posterior")))
                .andExpect(jsonPath("$.detalles", hasItem("indicadorCodigo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("numeroPeriodo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("valorMeta: no puede ser negativo")));
    }

    @Test
    void valorConMasDeDosDecimalesDevuelve400() throws Exception {
        registrar(cuerpoMeta("MONTO_RECAUDADO", 2026, 12, "100.555"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("valorMeta: admite hasta 12 enteros y 2 decimales")));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        registrar("{\"anio\": \"dos mil\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void buscaMetasFiltrandoPorIndicadorYAnio() throws Exception {
        mockMvc.perform(get(RUTA_METAS).param("indicadorCodigo", "PORC_RECAUDACION_EMISION").param("anio", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].indicadorCodigo", everyItem(is("PORC_RECAUDACION_EMISION"))))
                .andExpect(jsonPath("$[0].periodo").value("2026-10"))
                .andExpect(jsonPath("$[2].periodo").value("2026-12"));

        mockMvc.perform(get(RUTA_METAS).param("anio", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        mockMvc.perform(get(RUTA_METAS).param("anio", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void metaDeUnPeriodoConcluidoSeMarcaComoNoEditable() throws Exception {
        mockMvc.perform(get(RUTA_METAS).param("indicadorCodigo", "RECUPERACION_DEUDA").param("anio", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodo").value("2026-T3"))
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[1].periodo").value("2026-T4"))
                .andExpect(jsonPath("$[1].editable").value(true));
    }

    @Test
    void actualizaValorYObservacionDeUnaMetaVigente() throws Exception {
        long id = idDeMetaRegistrada(cuerpoMeta("MONTO_RECAUDADO", 2026, 12, "1000000"));

        mockMvc.perform(put(RUTA_METAS + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorMeta\": 1100000, \"observacion\": \"Ajuste por campaña\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorMeta").value(1100000))
                .andExpect(jsonPath("$.observacion").value("Ajuste por campaña"))
                .andExpect(jsonPath("$.periodo").value("2026-12"));

        mockMvc.perform(get(RUTA_METAS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorMeta").value(1100000));
    }

    @Test
    void noPermiteModificarLaMetaDeUnPeriodoConcluido() throws Exception {
        String metasDelAnio = mockMvc.perform(get(RUTA_METAS)
                        .param("indicadorCodigo", "RECUPERACION_DEUDA").param("anio", "2026"))
                .andReturn().getResponse().getContentAsString();
        long idTrimestreConcluido = ((Number) JsonPath.read(metasDelAnio, "$[0].id")).longValue();

        mockMvc.perform(put(RUTA_METAS + "/" + idTrimestreConcluido).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorMeta\": 40}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_CERRADO"));
    }

    @Test
    void eliminaUnaMetaYDejaDeEstarDisponible() throws Exception {
        long id = idDeMetaRegistrada(cuerpoMeta("MONTO_RECAUDADO", 2027, 1, "1000000"));

        mockMvc.perform(delete(RUTA_METAS + "/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RUTA_METAS + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("META_NO_ENCONTRADA"));
    }

    @Test
    void metaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(RUTA_METAS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("META_NO_ENCONTRADA"));
        mockMvc.perform(put(RUTA_METAS + "/999999").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorMeta\": 10}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(RUTA_METAS + "/999999").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA_METAS + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    private ResultActions registrar(String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA_METAS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private long idDeMetaRegistrada(String cuerpo) throws Exception {
        String respuesta = registrar(cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private static String cuerpoMeta(String indicadorCodigo, int anio, int numeroPeriodo, String valorMeta) {
        return """
                {"indicadorCodigo": "%s", "anio": %d, "numeroPeriodo": %d, "valorMeta": %s}
                """.formatted(indicadorCodigo, anio, numeroPeriodo, valorMeta);
    }
}
