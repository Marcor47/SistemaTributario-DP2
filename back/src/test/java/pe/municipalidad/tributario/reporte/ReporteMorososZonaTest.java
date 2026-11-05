package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
 * HU018: Generar reporte de contribuyentes morosos por zona y periodo (RF-024).
 * Criterios de aceptación:
 * 1. Dado que existe una carga del ETL del periodo, cuando genero el reporte, entonces
 *    coincide con el reporte de Contribuyentes Morosos de SAMNET en una muestra de control.
 * 2. Dado el reporte generado, cuando lo exporto, entonces se descarga en Excel o PDF.
 *
 * Columnas definidas en RF-024:
 * - código de contribuyente
 * - razón social
 * - régimen
 * - documento de identidad (tipo y DNI o RUC)
 * - domicilio fiscal (vía, número, departamento, interior, manzana, lote y distrito)
 * - deuda sin beneficio
 *
 * La muestra se calcula sobre la carga ETL de 2026-10-19. Con filtro de zona solo se suman los
 * arbitrios de los predios de esa zona; el predial no tiene zona.
 */
@PruebaIntegracion
@Transactional
class ReporteMorososZonaTest {

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

    @Test
    void morososPorZonaYPeriodoCoincideConLaCargaEtl() throws Exception {
        mvc.perform(get("/api/reportes/morosos-zona")
                        .param("periodo", "2026")
                        .param("zona", "Z2")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoReporte").value("MOROSOS"))
                .andExpect(jsonPath("$.filtrosAplicados.zona").value("Z2"))
                .andExpect(jsonPath("$.filtrosAplicados.periodo").value(2026))
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "058934", "061177")))
                // RF-024: Verificación de columnas obligatorias
                .andExpect(jsonPath("$.filas[0].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[0].razonSocial").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.filas[0].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[0].tipoDocumento").value("RUC"))
                .andExpect(jsonPath("$.filas[0].numeroDocumento").value("20456781239"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal").value("Jr. Los Precursores 220, San Miguel"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscalDesglosado.via").value("Jr. Los Precursores"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscalDesglosado.numero").value("220"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscalDesglosado.distrito").value("SAN MIGUEL"))
                // La muestra se calcula a la fecha de corte del último lote publicado de cobranza.
                /* 2026 de 021390 en Z2: enero a setiembre de arbitrios de sus 4 predios de esa zona (36 cuotas). */
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(4047.75))
                .andExpect(jsonPath("$.filas[0].deudaSinBeneficio").value(4047.75))
                .andExpect(jsonPath("$.filas[0].cuotasVencidas").value(36))
                .andExpect(jsonPath("$.filas[0].zonas", contains("Z1", "Z2", "Z3")));
    }

    @Test
    void conCargaDelETLDelPeriodoElReporteSeCalculaSobreElEtl() throws Exception {
        // Un lote publicado sin hechos no se usa: el reporte toma la carga del seed que sí trae hechos.
        LoteETL lote = new LoteETL(
                "ps_etl_cobranza",
                LocalDate.of(2026, 10, 19),
                TipoEjecucion.PROGRAMADA,
                LocalDateTime.of(2026, 10, 19, 3, 0));
        lote.publicar(100, 100, LocalDateTime.of(2026, 10, 19, 3, 15));
        lote = lotes.save(lote);
        assertThat(lote.getIdLote()).isNotNull();

        mvc.perform(get("/api/reportes/morosos-zona")
                        .param("periodo", "2026")
                        .param("zona", "Z1")
                        .session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "080245", "045067")))
                .andExpect(jsonPath("$.filas[2].razonSocial").value("CENTRO EDUCATIVO SOL DE LIMA E.I.R.L."))
                .andExpect(jsonPath("$.filas[2].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[2].domicilioFiscalDesglosado.via").value("Av. Faro Azul"))
                .andExpect(jsonPath("$.filas[2].deudaSinBeneficio", notNullValue()));
    }

    @Test
    void exportarMorososPorZonaAExcelDescargaXlsx() throws Exception {
        byte[] excel = mvc.perform(get("/api/reportes/morosos-zona/exportar")
                        .param("formato", "EXCEL")
                        .param("periodo", "2026")
                        .param("zona", "Z2")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("reporte_morosos_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(excel).isNotEmpty();
        // PKZIP signature para formato XLSX
        assertThat(excel[0]).isEqualTo((byte) 'P');
        assertThat(excel[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportarMorososPorZonaAPdfDescargaPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/reportes/morosos-zona/exportar")
                        .param("formato", "PDF")
                        .param("periodo", "2026")
                        .param("zona", "Z2")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("reporte_morosos_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).isNotEmpty();
        // %PDF signature
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void morososPorZonaSinSesionDevuelve401() throws Exception {
        mvc.perform(get("/api/reportes/morosos-zona"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void morososPorZonaConRolSinPermisoDevuelve403() throws Exception {
        mvc.perform(get("/api/reportes/morosos-zona")
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
