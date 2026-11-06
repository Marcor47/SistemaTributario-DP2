package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.EstadoLote;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU038: Generar el reporte de buenos pagadores por zona (RF-051).
 * Criterios de aceptación:
 * 1. Dado que existe una carga del ETL del periodo, cuando genero el reporte, entonces
 *    coincide con el reporte de Contribuyentes Puntuales de SAMNET en una muestra de control.
 * 2. Dado el reporte generado, cuando lo exporto, entonces se descarga en Excel o PDF.
 *
 * Columnas definidas en RF-051:
 * - código de contribuyente
 * - razón social
 * - régimen
 * - documento de identidad (tipo y DNI o RUC)
 * - domicilio fiscal
 * - monto emitido de predial y de arbitrios
 * - monto pagado de predial y de arbitrios
 */
@PruebaIntegracion
@Transactional
class ReporteBuenosPagadoresZonaTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;
    @Autowired
    private LoteETLRepository lotes;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void buenosPagadoresPorZonaYPeriodoCoincideConMuestraControlSAMNET() throws Exception {
        mvc.perform(get("/api/reportes/buenos-pagadores-zona")
                        .param("periodo", "2026")
                        .param("zona", "Z1")
                        .session(sesion(NombreRol.GERENCIA)))
                
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoReporte").value("BUENOS_PAGADORES"))
                .andExpect(jsonPath("$.filtrosAplicados.zona").value("Z1"))
                .andExpect(jsonPath("$.filtrosAplicados.periodo").value(2026))
                .andExpect(jsonPath("$.filas[*].codigo", contains("014502")))
                // RF-051: Verificación de columnas obligatorias
                .andExpect(jsonPath("$.filas[0].codigo").value("014502"))
                .andExpect(jsonPath("$.filas[0].razonSocial").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.filas[0].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[0].tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.filas[0].numeroDocumento").value("41028765"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal").value("Av. La Marina 1450, San Miguel"))
                /* 2026 de 014502: predial S/ 1 368,00 (2 predios) y arbitrios S/ 1 132,32, pagados en febrero. */
                .andExpect(jsonPath("$.filas[0].montoEmitidoPredial").value(1368.00))
                .andExpect(jsonPath("$.filas[0].montoEmitidoArbitrios").value(1132.32))
                .andExpect(jsonPath("$.filas[0].montoPagadoPredial").value(1368.00))
                .andExpect(jsonPath("$.filas[0].montoPagadoArbitrios").value(1132.32))
                .andExpect(jsonPath("$.filas[0].totalEmitido").value(2500.32))
                .andExpect(jsonPath("$.filas[0].totalPagado").value(2500.32))
                .andExpect(jsonPath("$.filas[0].cuotasPagadasATiempo").value(21))
                .andExpect(jsonPath("$.filas[0].zonas", contains("Z1", "Z2")));
    }

    @Test
    void conCargaDelETLDelPeriodoElReporteCoincideConSAMNET() throws Exception {
        // Carga previa del ETL para el periodo evaluado (prerrequisito HU025 / lote publicado)
        LoteETL lote = new LoteETL(
                "ps_etl_cobranza",
                LocalDate.of(2026, 10, 19),
                TipoEjecucion.PROGRAMADA,
                LocalDateTime.of(2026, 10, 19, 3, 0));
        lote.publicar(100, 100, LocalDateTime.of(2026, 10, 19, 3, 15));
        lote = lotes.save(lote);
        assertThat(lote.getIdLote()).isNotNull();

        mvc.perform(get("/api/reportes/buenos-pagadores-zona")
                        .param("periodo", "2026")
                        .param("zona", "Z3")
                        .session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("033218")))
                .andExpect(jsonPath("$.filas[0].razonSocial").value("RAMOS QUISPE, VÍCTOR HUGO"))
                .andExpect(jsonPath("$.filas[0].regimen").value("PENSIONISTA"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal").value("Calle Bahía 88, San Miguel"))
                /* Pensionista con un solo predio de vivienda: deduce 50 UIT y no paga predial; solo arbitrios. */
                .andExpect(jsonPath("$.filas[0].montoEmitidoPredial").value(0))
                .andExpect(jsonPath("$.filas[0].montoEmitidoArbitrios").value(472.08))
                .andExpect(jsonPath("$.filas[0].montoPagadoPredial").value(0))
                .andExpect(jsonPath("$.filas[0].montoPagadoArbitrios").value(472.08))
                .andExpect(jsonPath("$.filas[0].totalPagado").value(472.08));
    }

    @Test
    void exportarBuenosPagadoresPorZonaAExcelDescargaXlsx() throws Exception {
        byte[] excel = mvc.perform(get("/api/reportes/buenos-pagadores-zona/exportar")
                        .param("formato", "EXCEL")
                        .param("periodo", "2026")
                        .param("zona", "Z1")
                        .session(sesion(NombreRol.GERENCIA)))
                
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("reporte_buenos_pagadores_zona_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(excel).isNotEmpty();
        // PKZIP signature para formato XLSX
        assertThat(excel[0]).isEqualTo((byte) 'P');
        assertThat(excel[1]).isEqualTo((byte) 'K');
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var hoja = libro.getSheetAt(0);
            List<String> cabeceras = java.util.stream.IntStream.range(0, hoja.getRow(5).getLastCellNum())
                    .mapToObj(i -> hoja.getRow(5).getCell(i).getStringCellValue())
                    .toList();
            assertThat(cabeceras).contains("Código", "Razón Social", "Régimen", "Tipo Doc.", "N° Documento",
                    "Domicilio Fiscal", "Emitido Predial (S/)", "Emitido Arbitrios (S/)",
                    "Pagado Predial (S/)", "Pagado Arbitrios (S/)");
            assertThat(hoja.getRow(6).getCell(cabeceras.indexOf("Emitido Predial (S/)"))
                    .getNumericCellValue()).isEqualTo(1368.00);
            assertThat(hoja.getRow(6).getCell(cabeceras.indexOf("Pagado Arbitrios (S/)"))
                    .getNumericCellValue()).isEqualTo(1132.32);
        }
    }

    @Test
    void exportarBuenosPagadoresPorZonaAPdfDescargaPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/reportes/buenos-pagadores-zona/exportar")
                        .param("formato", "PDF")
                        .param("periodo", "2026")
                        .param("zona", "Z1")
                        .session(sesion(NombreRol.GERENCIA)))
                
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("reporte_buenos_pagadores_zona_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).isNotEmpty();
        // %PDF signature
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (var documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Emitido Predial", "Emitido Arbitrios", "Pagado Predial",
                    "Pagado Arbitrios", "1368.00", "1132.32", "014502");
        }
    }

    @Test
    void filtroDeTributoNoOcultaUnaDeudaEnElOtroTributo() throws Exception {
        jdbc.update("UPDATE samnet.obligacion SET monto_pagado = 0 "
                + "WHERE contribuyente_codigo = '014502' AND tributo = 'ARBITRIOS' "
                + "AND anio = 2026 AND numero_cuota = 1");
        mvc.perform(get("/api/reportes/buenos-pagadores-zona")
                        .param("periodo", "2026")
                        .param("zona", "Z1")
                        .param("tributo", "IMPUESTO_PREDIAL")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas").isEmpty());
    }

    @Test
    void periodoFueraDeRangoSeRechazaEnConsultaYExportacion() throws Exception {
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);
        mvc.perform(get("/api/reportes/buenos-pagadores-zona")
                        .param("periodo", "1999").session(gerencia))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/buenos-pagadores-zona/exportar")
                        .param("formato", "PDF").param("periodo", "2101").session(gerencia))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buenosPagadoresPorZonaSinSesionDevuelve401() throws Exception {
        mvc.perform(get("/api/reportes/buenos-pagadores-zona"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buenosPagadoresPorZonaConRolSinPermisoDevuelve403() throws Exception {
        mvc.perform(get("/api/reportes/buenos-pagadores-zona")
                        .session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession sesion(NombreRol rol) {
        String nombre = "usuario_" + rol.name().toLowerCase() + "_" + System.nanoTime();
        Usuario usuario = usuarios.save(new Usuario(nombre, "Usuario " + rol, nombre + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}

