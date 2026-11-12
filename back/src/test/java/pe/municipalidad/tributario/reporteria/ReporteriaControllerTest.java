package pe.municipalidad.tributario.reporteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.reporte.RestriccionReporteRepository;
import pe.municipalidad.tributario.reporte.RestriccionReporte;
import pe.municipalidad.tributario.reporte.TipoReporte;
import pe.municipalidad.tributario.reporte.DimensionRestringida;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * Plataforma analítica de Rentas. Muestra de control con fecha de corte 2026-10-19, igual a la del dashboard (HU011):
 *
 *   Contrib.  Zona  Tributo           Vence       Emitido  Pagado  Insoluto  Pago        Canal       ¿Exigible?
 *   C1        Z01   IMPUESTO_PREDIAL  2026-02-27    400     400       0      2026-02-20  EN_LINEA    sí, a tiempo
 *   C1        Z01   IMPUESTO_PREDIAL  2026-05-29    400       0     400      -           -           sí
 *   C1        Z01   ARBITRIOS         2026-09-30     50      30      20      2026-09-15  EN_LINEA    sí, pago parcial
 *   C2        Z02   ARBITRIOS         2026-09-30     50      50       0      2026-10-05  VENTANILLA  sí, fuera de plazo
 *   C2        Z02   ARBITRIOS         2026-10-19     30       0      30      -           -           no: vence el día de corte
 *   C2        Z02   IMPUESTO_PREDIAL  2026-11-30    400       0     400      -           -           no: aún no vence
 *   C2        Z02   ARBITRIOS         2025-12-31     50       0      50      -           -           sí, de 2025
 *
 *   Enero a octubre de 2026: emitido 900, cobrado 480, recaudado 480, deuda 420.
 */
@PruebaIntegracion
@Transactional
class ReporteriaControllerTest {

    private static final String DATOS = "/api/reporteria/datos";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoteETLRepository lotes;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;
    @Autowired private RestriccionReporteRepository restricciones;

    private MockHttpSession gerencia;
    private LoteETL lote;

    @BeforeEach
    void cargarMuestraDeControl() {
        gerencia = sesion(NombreRol.GERENCIA, "gerencia_analitica");
        lote = new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(3, 0));
        lote.publicar(7, 7, FECHA_CORTE.atTime(3, 5));
        lotes.saveAndFlush(lote);

        contribuyente("SEUD-ANALITICA-C1");
        contribuyente("SEUD-ANALITICA-C2");
        long z01 = predio("SEUD-PREDIO-ANALITICA-1", "Z01");
        long z02 = predio("SEUD-PREDIO-ANALITICA-2", "Z02");
        cuota("SEUD-ANALITICA-C1", z01, "IMPUESTO_PREDIAL", 2026, 1, "2026-02-27", "400", "0", "PAGADA", "2026-02-20", "EN_LINEA");
        cuota("SEUD-ANALITICA-C1", z01, "IMPUESTO_PREDIAL", 2026, 2, "2026-05-29", "400", "400", "VENCIDA", null, null);
        cuota("SEUD-ANALITICA-C1", z01, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "20", "VENCIDA", "2026-09-15", "EN_LINEA");
        cuota("SEUD-ANALITICA-C2", z02, "ARBITRIOS", 2026, 9, "2026-09-30", "50", "0", "PAGADA", "2026-10-05", "VENTANILLA");
        cuota("SEUD-ANALITICA-C2", z02, "ARBITRIOS", 2026, 10, "2026-10-19", "30", "30", "VENCIDA", null, null);
        cuota("SEUD-ANALITICA-C2", z02, "IMPUESTO_PREDIAL", 2026, 4, "2026-11-30", "400", "400", "PENDIENTE", null, null);
        cuota("SEUD-ANALITICA-C2", z02, "ARBITRIOS", 2025, 12, "2025-12-31", "50", "50", "VENCIDA", null, null);
    }

    @Test
    void lasCifrasDelPeriodoCoincidenConElDashboard() throws Exception {
        mvc.perform(get("/api/dashboard").param("anio", "2026").session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.montoEmitido").value(900))
                .andExpect(jsonPath("$.resumen.montoRecaudado").value(480))
                .andExpect(jsonPath("$.resumen.montoVencidoNoPagado").value(420));

        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.emitido").value(900.0))
                .andExpect(jsonPath("$.totales.cobrado").value(480.0))
                .andExpect(jsonPath("$.totales.recaudado").value(480.0))
                .andExpect(jsonPath("$.totales.deuda").value(420.0))
                .andExpect(jsonPath("$.totales.cuotas").value(4))
                .andExpect(jsonPath("$.totales.cuotasATiempo").value(1))
                .andExpect(jsonPath("$.totales.contribuyentes").value(2))
                .andExpect(jsonPath("$.totales.morosos").value(1))
                .andExpect(jsonPath("$.totales.buenosPagadores").value(0));
    }

    @Test
    void elCatalogoSaleDeLaCargaYNoIncluyeLoQueAunNoVence() throws Exception {
        mvc.perform(get("/api/reporteria/catalogo").session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.primerMes").value("2025-12"))
                .andExpect(jsonPath("$.zonas[*].codigo", contains("Z01", "Z02")))
                .andExpect(jsonPath("$.tributos[*].codigo", contains("IMPUESTO_PREDIAL", "ARBITRIOS")))
                .andExpect(jsonPath("$.segmentos", hasItem(CuboReporteria.SIN_SEGMENTO)));
    }

    @Test
    void agregaPorMesZonaTributoYPagoSegunLaFechaDePago() throws Exception {
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(jsonPath("$.porMes.emitido", hasSize(10)))
                .andExpect(jsonPath("$.porMes.emitido[1]").value(400.0))
                .andExpect(jsonPath("$.porMes.recaudado[1]").value(400.0))
                .andExpect(jsonPath("$.porMes.recaudado[8]").value(30.0))
                .andExpect(jsonPath("$.porMes.recaudado[9]").value(50.0))
                .andExpect(jsonPath("$.porZona.emitido[0]").value(850.0))
                .andExpect(jsonPath("$.porZona.deuda[0]").value(420.0))
                .andExpect(jsonPath("$.porZona.morosos", contains(1, 0)))
                .andExpect(jsonPath("$.porTributo.emitido", contains(800.0, 100.0)))
                .andExpect(jsonPath("$.zonaTributoDeuda[0]", contains(400.0, 20.0)))
                .andExpect(jsonPath("$.canal", contains(0, 1)))
                .andExpect(jsonPath("$.anticipacion", contains(0, 0, 1, 0)))
                .andExpect(jsonPath("$.antiguedad", contains(20.0, 400.0, 0.0, 0.0)));
    }

    @Test
    void losFiltrosRestringenLasCuotas() throws Exception {
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").param("zona", "Z02").session(gerencia))
                .andExpect(jsonPath("$.totales.emitido").value(50.0))
                .andExpect(jsonPath("$.totales.deuda").value(0.0))
                .andExpect(jsonPath("$.totales.contribuyentes").value(1));
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").param("tributo", "ARBITRIOS").session(gerencia))
                .andExpect(jsonPath("$.totales.emitido").value(100.0))
                .andExpect(jsonPath("$.totales.cobrado").value(80.0));
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").param("segmento", "No existe").session(gerencia))
                .andExpect(jsonPath("$.totales.cuotas").value(0));
        mvc.perform(get(DATOS).param("desde", "2025-12").param("hasta", "2025-12").session(gerencia))
                .andExpect(jsonPath("$.totales.deuda").value(50.0));
    }

    @Test
    void elRankingDeMorososYElContribuyenteDestacado() throws Exception {
        String seudonimoC2 = "SEUD-ANALITICA-C2";
        Integer idC2 = jdbc.queryForObject("""
                SELECT posicion - 1 FROM (
                    SELECT d.seudonimo_contribuyente, row_number() OVER (ORDER BY d.seudonimo_contribuyente) AS posicion
                    FROM analitica.dim_contribuyente d
                    WHERE EXISTS (SELECT 1 FROM analitica.hecho_cobranza h WHERE h.id_lote = ?
                                  AND h.seudonimo_contribuyente = d.seudonimo_contribuyente)) t
                WHERE t.seudonimo_contribuyente = ?""", Integer.class, lote.getIdLote(), seudonimoC2);

        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10")
                        .param("destacado", String.valueOf(idC2)).session(gerencia))
                .andExpect(jsonPath("$.top.morosos", hasSize(1)))
                .andExpect(jsonPath("$.top.morosos[0].nombre").value("Contrib. SEUD-ANA"))
                .andExpect(jsonPath("$.top.morosos[0].deuda").value(420.0))
                .andExpect(jsonPath("$.top.morosos[0].cuotasVencidas").value(2))
                .andExpect(jsonPath("$.top.morosos[0].tributoPrincipal").value(0))
                .andExpect(jsonPath("$.top.morosos[0].ultimoPago").value("2026-09"))
                .andExpect(jsonPath("$.destacado.fila.id").value(idC2))
                .andExpect(jsonPath("$.destacado.puestos.resumen").value(2));
    }

    @Test
    void unPagoHastaElVencimientoCuentaComoBuenPagador() throws Exception {
        jdbc.update("UPDATE analitica.hecho_cobranza SET fecha_pago = DATE '2026-09-30' WHERE id_lote = ? "
                + "AND seudonimo_contribuyente = 'SEUD-ANALITICA-C2' AND estado_pago = 'PAGADA'", lote.getIdLote());
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(jsonPath("$.totales.buenosPagadores").value(1))
                .andExpect(jsonPath("$.totales.recaudadoBuenosPagadores").value(50.0))
                .andExpect(jsonPath("$.top.buenos", hasSize(1)))
                .andExpect(jsonPath("$.anticipacion", contains(0, 0, 1, 1)));
    }

    @Test
    void losPricosUsanElUmbralConfigurable() throws Exception {
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(jsonPath("$.pricos.cantidad").value(0));

        jdbc.update("UPDATE seguridad.parametro_configuracion SET valor = '500' WHERE clave = 'UMBRAL_PRICO'");

        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(jsonPath("$.pricos.cantidad").value(1))
                .andExpect(jsonPath("$.pricos.emitido").value(850.0))
                .andExpect(jsonPath("$.puntosPricos", hasSize(1)))
                .andExpect(jsonPath("$.puntosPricos[0].porcentajeCobrado").value(50.59))
                .andExpect(jsonPath("$.pricosPorMes.emitidoResto[8]").value(50.0));
    }

    @Test
    void laMetaSaleDelIndicadorYDelAlcanceDeLosFiltros() throws Exception {
        jdbc.update("""
                UPDATE analitica.meta_indicador SET valor_meta = 85, umbral_desvio = 15
                WHERE indicador_codigo = 'PORC_RECAUDACION_EMISION' AND anio = 2026 AND numero_periodo = 10""");
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(jsonPath("$.meta.valor").value(85.0))
                .andExpect(jsonPath("$.meta.umbralDesvio").value(15.0));
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").param("zona", "Z01").session(gerencia))
                .andExpect(jsonPath("$.meta").doesNotExist());
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-09").session(gerencia))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void rechazaUnPeriodoInvalido() throws Exception {
        mvc.perform(get(DATOS).param("desde", "2026-10").param("hasta", "2026-01").session(gerencia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
        mvc.perform(get(DATOS).param("desde", "2000-01").param("hasta", "2026-01").session(gerencia))
                .andExpect(status().isBadRequest());
        mvc.perform(get(DATOS).param("desde", "enero").param("hasta", "2026-01").session(gerencia))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloGerenciaYAnalistaDeRentasPuedenConsultar() throws Exception {
        mvc.perform(get("/api/reporteria/catalogo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reporteria/catalogo").session(sesion(NombreRol.VENTANILLA, "ventanilla_analitica")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reporteria/catalogo").session(sesion(NombreRol.ANALISTA_RENTAS, "analista_analitica")))
                .andExpect(status().isOk());
    }

    @Test
    void lasVistasSonDeSuDuenoYElNombreSeActualiza() throws Exception {
        String peticion = """
                {"nombre": "Morosidad 2026", "estado": {"tab": "morosos", "filtros": {"periodo": "2026"}}}""";
        mvc.perform(post("/api/reporteria/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(peticion).session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado.tab").value("morosos"));
        mvc.perform(post("/api/reporteria/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(peticion.replace("morosos", "pricos")).session(gerencia))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reporteria/vistas").session(gerencia))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado.tab").value("pricos"));

        MockHttpSession otro = sesion(NombreRol.ANALISTA_RENTAS, "analista_vistas");
        mvc.perform(get("/api/reporteria/vistas").session(otro)).andExpect(jsonPath("$", hasSize(0)));
        Long idVista = jdbc.queryForObject("SELECT max(id_vista) FROM operativo.vista_analitica", Long.class);
        mvc.perform(delete("/api/reporteria/vistas/" + idVista).with(csrf()).session(otro)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/reporteria/vistas/" + idVista).with(csrf()).session(gerencia)).andExpect(status().isNoContent());
        mvc.perform(get("/api/reporteria/vistas").session(gerencia)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void guardarVistaExigeCsrfYNombre() throws Exception {
        mvc.perform(post("/api/reporteria/vistas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"a\", \"estado\": {}}").session(gerencia))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reporteria/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \" \", \"estado\": {}}").session(gerencia))
                .andExpect(status().isBadRequest());
    }

    private static final String EXPORTACION = """
            {"pestana": "morosos", "reporte": "Morosos", "corte": "19/10/2026", "filtros": "2026 · todas las zonas",
             "indicadores": [["Deuda vencida", "S/ 420", "Cuotas vencidas − sin pagar"]],
             "graficos": [{"titulo": "Deuda vencida por zona", "tipo": "Tabla", "encabezados": ["Zona", "Deuda vencida"],
                           "filas": [["Z01", 420.5]]}],
             "ranking": {"titulo": "Top 10 morosos", "encabezados": ["Contribuyente", "Deuda vencida"],
                         "filas": [["Contrib. ABC", 420.5]]}}""";

    @Test
    void filtraLaZonaAntesDeLosTotalesSinCerrarLaPlataformaNiContaminarLaCache() throws Exception {
        restringirZona("Z01");
        mvc.perform(get("/api/reporteria/catalogo").session(gerencia)).andExpect(status().isOk());
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.emitido").value(50.0))
                .andExpect(jsonPath("$.totales.deuda").value(0.0));
        var analista = sesion(NombreRol.ANALISTA_RENTAS, "analista_sin_restriccion");
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(analista))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.emitido").value(900.0));
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10")
                        .param("zona", "Z01").param("destacado", "0").session(gerencia))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.emitido").value(0.0))
                .andExpect(jsonPath("$.destacado").isEmpty());
    }

    @Test
    void exportaSoloDatosAutorizadosAunqueElNavegadorEnvieImportesYFilasAjenos() throws Exception {
        restringirZona("Z01");
        String peticion = EXPORTACION.replace("Contrib. ABC", "DATO_NO_AUTORIZADO")
                .replace("\"pestana\": \"morosos\"", "\"pestana\": \"resumen\"");
        peticion = peticion.substring(0, peticion.length() - 1)
                + ",\"consulta\":{\"desde\":\"2026-01\",\"hasta\":\"2026-10\"}}";
        byte[] pdf = mvc.perform(post("/api/reporteria/exportar").param("formato", "PDF").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(peticion).session(gerencia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var documento = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documento)).contains("Contribuyentes autorizados", "50.00")
                    .doesNotContain("DATO_NO_AUTORIZADO", "420.50");
        }
    }

    private void restringirZona(String zona) {
        restricciones.saveAndFlush(new RestriccionReporte(roles.findByNombre(NombreRol.GERENCIA).orElseThrow(),
                TipoReporte.MOROSOS, DimensionRestringida.ZONA, zona, false));
    }

    @Test
    void exportaExcelYPdfYQuedaEnLaAuditoria() throws Exception {
        byte[] excel = mvc.perform(post("/api/reporteria/exportar").param("formato", "EXCEL").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EXPORTACION).session(gerencia))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"plataforma-analitica-morosos-2026-10-19.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            assertThat(libro.getSheet("Ranking").getRow(2).getCell(1).getNumericCellValue()).isEqualTo(420.5);
        }

        byte[] pdf = mvc.perform(post("/api/reporteria/exportar").param("formato", "PDF").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EXPORTACION).session(gerencia))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Deuda vencida por zona", "Top 10 morosos", "420.50", "Cuotas vencidas - sin pagar");
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.registro_auditoria "
                + "WHERE accion = 'EXPORTAR' AND entidad = 'PlataformaAnalitica'", Long.class)).isEqualTo(2);
    }

    @Test
    void exportarRechazaUnaImagenQueNoEsPng() throws Exception {
        mvc.perform(post("/api/reporteria/exportar").param("formato", "PDF").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EXPORTACION.replace("\"tipo\": \"Tabla\"", "\"tipo\": \"Tabla\", \"imagen\": \"http://otro\""))
                        .session(gerencia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IMAGEN_INVALIDA"));
    }

    @Test
    void sinLaUrlDelServicioElAsistenteNoEstaDisponibleYLaPlataformaSigue() throws Exception {
        mvc.perform(get("/api/reporteria/asistente/estado").session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.mensaje").value("El servicio de IA no está configurado en este servidor."));
        mvc.perform(post("/api/reporteria/asistente/preguntas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pregunta\": \"¿Cuánto se debe?\", \"desde\": \"2026-01\", \"hasta\": \"2026-10\"}")
                        .session(gerencia))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"));
        mvc.perform(get(DATOS).param("desde", "2026-01").param("hasta", "2026-10").session(gerencia))
                .andExpect(status().isOk());
    }

    private void contribuyente(String seudonimo) {
        jdbc.update("INSERT INTO analitica.dim_contribuyente (seudonimo_contribuyente, tipo_persona) "
                + "VALUES (?, 'NATURAL') ON CONFLICT DO NOTHING", seudonimo);
    }

    private long predio(String seudonimo, String zona) {
        return jdbc.queryForObject("INSERT INTO analitica.dim_predio (seudonimo_predio, zona, tipo_predio) "
                + "VALUES (?, ?, 'URBANO') RETURNING id_predio", Long.class, seudonimo, zona);
    }

    private void cuota(String contribuyente, long idPredio, String tributo, int anio, int cuota, String vence,
            String emitido, String insoluto, String estado, String fechaPago, String canal) {
        BigDecimal montoEmitido = new BigDecimal(emitido);
        BigDecimal montoInsoluto = new BigDecimal(insoluto);
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza
                    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
                     monto_emitido, monto_insoluto, monto_pagado, estado_pago, fecha_pago, canal_pago)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, lote.getIdLote(), contribuyente, idPredio, tributo, anio, cuota, LocalDate.parse(vence),
                montoEmitido, montoInsoluto, montoEmitido.subtract(montoInsoluto), estado,
                fechaPago == null ? null : LocalDate.parse(fechaPago), canal);
    }

    private MockHttpSession sesion(NombreRol rol, String username) {
        Usuario usuario = usuarios.save(new Usuario(username, "Usuario " + username, username + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
