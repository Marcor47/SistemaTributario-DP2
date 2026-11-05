package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU007: Guardar y exportar una vista de reporte (RF-008, RF-044, RF-059, RF-060, RNF-023).
 */
@PruebaIntegracion
@Transactional
class VistaReporteControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;
    @Autowired
    private RestriccionReporteRepository restricciones;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private SamnetGateway samnet;

    @Test
    void guardarVistaPersonalizadaConFiltrosYColumnas() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente1");
        String peticionJson = """
                {
                  "nombre": "Morosos Zona 2 con Arbitrios",
                  "tipoGrafica": "BARRAS",
                  "filtros": {
                    "zona": "Z2",
                    "tributo": "ARBITRIOS",
                    "montoMinimo": 1000
                  },
                  "columnasVisibles": ["codigo", "nombre", "deudaVencida", "nombreEtapa"],
                  "columnasDetalleVisibles": ["codigoPredio", "tributo", "montoConBeneficio"],
                  "series": ["deudaVencida"],
                  "orden": "deudaVencida:desc"
                }
                """;

        mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peticionJson)
                        .session(sesion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVista", notNullValue()))
                .andExpect(jsonPath("$.tipoReporte").value("MOROSOS"))
                .andExpect(jsonPath("$.nombre").value("Morosos Zona 2 con Arbitrios"))
                .andExpect(jsonPath("$.tipoGrafica").value("BARRAS"))
                .andExpect(jsonPath("$.filtros.zona").value("Z2"))
                .andExpect(jsonPath("$.filtros.tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.filtros.montoMinimo").value(1000))
                .andExpect(jsonPath("$.columnasVisibles", contains("codigo", "nombre", "deudaVencida", "nombreEtapa")))
                .andExpect(jsonPath("$.columnasDetalleVisibles", contains("codigoPredio", "tributo", "montoConBeneficio")))
                .andExpect(jsonPath("$.series", contains("deudaVencida")))
                .andExpect(jsonPath("$.orden").value("deudaVencida:desc"))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()));
    }

    @Test
    void abrirVistaGuardadaEjecutaElReporteConLaMismaConfiguracion() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_abrir");
        String guardarJson = """
                {
                  "nombre": "PRICOS del 2025",
                  "tipoGrafica": "TABLA",
                  "filtros": {
                    "anio": 2025
                  },
                  "columnasVisibles": ["codigo", "nombre", "emitidoAnio", "pagadoAnio", "porcentajePagado"],
                  "orden": "emitidoAnio:desc"
                }
                """;

        String respuestaGuardar = mvc.perform(post("/api/reportes/PRICOS/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guardarJson)
                        .session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number idVista = com.jayway.jsonpath.JsonPath.read(respuestaGuardar, "$.idVista");

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/abrir").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vista.idVista").value(idVista))
                .andExpect(jsonPath("$.vista.nombre").value("PRICOS del 2025"))
                .andExpect(jsonPath("$.vista.tipoGrafica").value("TABLA"))
                .andExpect(jsonPath("$.vista.columnasVisibles", hasSize(5)))
                .andExpect(jsonPath("$.reporte.tipoReporte").value("PRICOS"))
                .andExpect(jsonPath("$.reporte.filtrosAplicados.anio").value(2025))
                .andExpect(jsonPath("$.reporte.filas[*].codigo", contains("080245", "021390", "045067", "014502")))
                .andExpect(jsonPath("$.reporte.filas[0].emitidoAnio").value(96564.60))
                .andExpect(jsonPath("$.reporte.filas[0].porcentajePagado").value(100.00));
    }

    @Test
    void vistaPricosConservaCriterioLimiteYPermiteExportarColumnasHu037() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_pricos_hu007");
        String guardarJson = """
                {
                  "nombre": "PRICOS por deuda",
                  "tipoGrafica": "TABLA",
                  "filtros": { "criterio": "DEUDA_TOTAL", "limite": 2 },
                  "columnasVisibles": ["codigo", "razonSocial", "baseImponible", "deudaActualPredial"],
                  "ordenColumnas": ["codigo", "razonSocial", "baseImponible", "deudaActualPredial"]
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/PRICOS/vistas").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(guardarJson).session(sesion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filtros.criterio").value("DEUDA_TOTAL"))
                .andExpect(jsonPath("$.filtros.limite").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/abrir").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporte.criterio").value("DEUDA_TOTAL"))
                .andExpect(jsonPath("$.reporte.limite").value(2))
                .andExpect(jsonPath("$.reporte.filas", hasSize(2)));

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/exportar")
                        .param("formato", "EXCEL").session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void listarVistasPorTipoYListarTodas() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.ANALISTA_RENTAS, "analista_listar");

        String vista1 = """
                {
                  "nombre": "Vista Morosos A",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre", "deudaVencida"]
                }
                """;
        String vista2 = """
                {
                  "nombre": "Vista PRICOS B",
                  "tipoGrafica": "CIRCULAR",
                  "columnasVisibles": ["codigo", "nombre", "emitidoAnio"]
                }
                """;

        mvc.perform(post("/api/reportes/MOROSOS/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(vista1).session(sesion))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/reportes/PRICOS/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(vista2).session(sesion))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/reportes/MOROSOS/vistas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Vista Morosos A"));

        mvc.perform(get("/api/reportes/vistas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].nombre", containsInAnyOrder("Vista Morosos A", "Vista PRICOS B")));
    }

    @Test
    void actualizarVistaGuardada() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_actualizar");
        String guardarJson = """
                {
                  "nombre": "Vista Inicial",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/BUENOS_PAGADORES/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guardarJson)
                        .session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        String actualizarJson = """
                {
                  "nombre": "Vista Modificada",
                  "tipoGrafica": "LINEAS",
                  "columnasVisibles": ["codigo", "nombre", "cuotasPagadasATiempo", "pagadoAnio"],
                  "orden": "pagadoAnio:desc"
                }
                """;

        mvc.perform(put("/api/reportes/vistas/" + idVista)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizarJson)
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idVista").value(idVista))
                .andExpect(jsonPath("$.nombre").value("Vista Modificada"))
                .andExpect(jsonPath("$.tipoGrafica").value("LINEAS"))
                .andExpect(jsonPath("$.columnasVisibles", hasSize(4)))
                .andExpect(jsonPath("$.orden").value("pagadoAnio:desc"));
    }

    @Test
    void eliminarVistaGuardada() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_eliminar");
        String guardarJson = """
                {
                  "nombre": "Vista Para Borrar",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guardarJson)
                        .session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        mvc.perform(delete("/api/reportes/vistas/" + idVista).with(csrf()).session(sesion))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/reportes/vistas/" + idVista).session(sesion))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VISTA_NO_ENCONTRADA"));
    }

    @Test
    void privacidadDeVistaSoloLaAbreSuDuenio() throws Exception {
        MockHttpSession sesionUsuarioA = sesion(NombreRol.GERENCIA, "gerente_a");
        MockHttpSession sesionUsuarioB = sesion(NombreRol.ANALISTA_RENTAS, "analista_b");

        String guardarJson = """
                {
                  "nombre": "Vista Privada",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guardarJson)
                        .session(sesionUsuarioA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        mvc.perform(get("/api/reportes/vistas/" + idVista).session(sesionUsuarioB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VISTA_NO_ENCONTRADA"));

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/abrir").session(sesionUsuarioB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VISTA_NO_ENCONTRADA"));
    }

    @Test
    void guardarVistaConNombreExistenteSobrescribeLaVista() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_duplicado");
        String primeraPeticion = """
                {
                  "nombre": "Mi Reporte Favorito",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """;
        String segundaPeticion = """
                {
                  "nombre": "Mi Reporte Favorito",
                  "tipoGrafica": "BARRAS",
                  "columnasVisibles": ["codigo", "razonSocial", "deudaVencida"],
                  "columnasDetalleVisibles": ["codigoPredio", "montoConBeneficio"],
                  "orden": "deudaVencida:desc"
                }
                """;

        String primeraRespuesta = mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(primeraPeticion).session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number primerId = com.jayway.jsonpath.JsonPath.read(primeraRespuesta, "$.idVista");

        String segundaRespuesta = mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(segundaPeticion).session(sesion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVista").value(primerId))
                .andExpect(jsonPath("$.tipoGrafica").value("BARRAS"))
                .andExpect(jsonPath("$.columnasVisibles", contains("codigo", "razonSocial", "deudaVencida")))
                .andExpect(jsonPath("$.columnasDetalleVisibles", contains("codigoPredio", "montoConBeneficio")))
                .andExpect(jsonPath("$.orden").value("deudaVencida:desc"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number segundoId = com.jayway.jsonpath.JsonPath.read(segundaRespuesta, "$.idVista");
        assertThat(segundoId).isEqualTo(primerId);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operativo.vista_reporte v JOIN seguridad.usuario u ON u.id_usuario = v.id_usuario"
                        + " WHERE u.nombre_usuario = 'gerente_duplicado' AND v.nombre = 'Mi Reporte Favorito'",
                Integer.class);
        assertThat(total).isEqualTo(1);
    }

    @Test
    void exportarReporteAExcelGeneraArchivoXlsxYRegistraSolicitud() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_excel");

        byte[] contenido = mvc.perform(get("/api/reportes/MOROSOS/exportar")
                        .param("formato", "EXCEL")
                        .param("zona", "Z2")
                        .param("tributo", "ARBITRIOS")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-morosos-20261019.xlsx\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(contenido).isNotEmpty();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("MUNICIPALIDAD");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("Reporte de morosos");
        }

        List<String> formatos = jdbc.queryForList("SELECT formato_exportacion FROM operativo.solicitud_reporte s"
                + " JOIN seguridad.usuario u ON u.id_usuario = s.id_usuario"
                + " WHERE u.nombre_usuario = 'gerente_excel' AND s.tipo_reporte = 'MOROSOS'", String.class);
        assertThat(formatos).contains("EXCEL");
    }

    @Test
    void exportarReporteAPdfGeneraArchivoPdfYRegistraSolicitud() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.ANALISTA_RENTAS, "analista_pdf");

        byte[] contenido = mvc.perform(get("/api/reportes/PRICOS/exportar")
                        .param("formato", "PDF")
                        .param("anio", "2026")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-pricos-20261019.pdf\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(contenido).isNotEmpty();
        assertThat(new String(contenido, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (var doc = Loader.loadPDF(contenido)) {
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto)
                    .contains("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL")
                    .contains("Reporte de PRICOS")
                    .contains("INVERSIONES COSTA AZUL S.A.C.");
        }

        List<String> formatos = jdbc.queryForList("SELECT formato_exportacion FROM operativo.solicitud_reporte s"
                + " JOIN seguridad.usuario u ON u.id_usuario = s.id_usuario"
                + " WHERE u.nombre_usuario = 'analista_pdf' AND s.tipo_reporte = 'PRICOS'", String.class);
        assertThat(formatos).contains("PDF");
    }

    @Test
    void exportarVistaGuardadaAplicaColumnasDelDetalleYOrden() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_exp_detalle_vista");
        String guardarJson = """
                {
                  "nombre": "Morosos con detalle",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "razonSocial", "deudaVencida"],
                  "columnasDetalleVisibles": ["codigoPredio", "montoConBeneficio"],
                  "orden": "deudaVencida:desc"
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/MOROSOS/vistas")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(guardarJson).session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        byte[] pdf = mvc.perform(get("/api/reportes/vistas/" + idVista + "/exportar")
                        .param("formato", "PDF")
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var doc = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto)
                    .contains("Detalle de obligaciones")
                    .contains("Predio")
                    .contains("Con Beneficio");
        }
    }

    @Test
    void exportarMorososAplicaOrdenColumnasYDetalle() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_export_detalle");

        byte[] excel = mvc.perform(get("/api/reportes/MOROSOS/exportar")
                        .param("formato", "EXCEL")
                        .param("columnas", "codigo", "razonSocial", "deudaVencida")
                        .param("columnasDetalle", "anio", "codigoPredio", "montoConBeneficio")
                        .param("orden", "deudaVencida:desc")
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Código");

            // El orden solicitado (deudaVencida:desc) se refleja en las filas exportadas.
            double primeraDeuda = sheet.getRow(6).getCell(2).getNumericCellValue();
            double segundaDeuda = sheet.getRow(7).getCell(2).getNumericCellValue();
            assertThat(primeraDeuda).isGreaterThanOrEqualTo(segundaDeuda);

            boolean tieneTituloDetalle = false;
            boolean tieneCabeceraPredio = false;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var fila = sheet.getRow(i);
                if (fila == null) {
                    continue;
                }
                if (fila.getCell(0) != null
                        && "Detalle de obligaciones".equals(fila.getCell(0).getStringCellValue())) {
                    tieneTituloDetalle = true;
                }
                for (var celda : fila) {
                    if ("Predio".equals(celda.toString())) {
                        tieneCabeceraPredio = true;
                    }
                }
            }
            assertThat(tieneTituloDetalle).isTrue();
            assertThat(tieneCabeceraPredio).isTrue();
        }

        byte[] pdf = mvc.perform(get("/api/reportes/MOROSOS/exportar")
                        .param("formato", "PDF")
                        .param("columnas", "codigo", "razonSocial", "deudaVencida")
                        .param("columnasDetalle", "anio", "codigoPredio", "montoConBeneficio")
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var doc = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto)
                    .contains("Detalle de obligaciones")
                    .contains("Predio")
                    .contains("Con Beneficio");
        }
    }

    @Test
    void exportarConColumnaDeDetalleInvalidaDevuelve400() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_export_detalle_invalido");
        mvc.perform(get("/api/reportes/MOROSOS/exportar")
                        .param("formato", "EXCEL")
                        .param("columnasDetalle", "columna_fantasma")
                        .session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COLUMNA_NO_VALIDA"));
    }

    @Test
    void exportarVistaGuardadaAExcelYPdf() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_exp_vista");
        String guardarJson = """
                {
                  "nombre": "Buenos Pagadores 2026",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre", "pagadoAnio"]
                }
                """;

        String guardado = mvc.perform(post("/api/reportes/BUENOS_PAGADORES/vistas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guardarJson)
                        .session(sesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Number idVista = com.jayway.jsonpath.JsonPath.read(guardado, "$.idVista");

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/exportar")
                        .param("formato", "EXCEL")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mvc.perform(get("/api/reportes/vistas/" + idVista + "/exportar")
                        .param("formato", "PDF")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
    }

    @Test
    void exportarConFormatoInvalidoDevuelve400() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_formato_invalido");
        mvc.perform(get("/api/reportes/MOROSOS/exportar").param("formato", "NINGUNO").session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_SOPORTADO"));
    }

    @Test
    void validacionFiltrosYColumnasInvalidasDevuelven400() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "gerente_validaciones");

        // Rango de años inválido
        String morososConRangoInvalido = """
                {
                  "nombre": "Morosos Malos",
                  "filtros": { "anioDesde": 2026, "anioHasta": 2025 },
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """;
        mvc.perform(post("/api/reportes/MOROSOS/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(morososConRangoInvalido).session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));

        // Columna inexistente
        String columnaInvalida = """
                {
                  "nombre": "Columna Invalida",
                  "columnasVisibles": ["codigo", "columna_fantasma"]
                }
                """;
        mvc.perform(post("/api/reportes/MOROSOS/vistas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(columnaInvalida).session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COLUMNA_NO_VALIDA"));
    }

    @Test
    void sinSesionDevuelve401YRolNoAutorizadoDevuelve403() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS/vistas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reportes/MOROSOS/exportar").param("formato", "EXCEL")).andExpect(status().isUnauthorized());

        MockHttpSession sesionVentanilla = sesion(NombreRol.VENTANILLA, "usuario_ventanilla");
        mvc.perform(get("/api/reportes/MOROSOS/vistas").session(sesionVentanilla)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/MOROSOS/exportar").param("formato", "EXCEL").session(sesionVentanilla)).andExpect(status().isForbidden());
    }

    private MockHttpSession sesion(NombreRol rol, String username) {
        Usuario usuario = usuarios.save(new Usuario(username, "Usuario " + username, username + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
