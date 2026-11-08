package pe.municipalidad.tributario.reporte;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU032: Personalizar la visualización de un reporte dentro de un conjunto predefinido de opciones (RF-008, RF-044).
 * GERENCIA tiene REPORTES:PERSONALIZAR (V32); ANALISTA_RENTAS no.
 */
@PruebaIntegracion
@Transactional
class PersonalizacionReporteControllerTest {

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

    /* ===== Opciones de visualización ===== */

    @Test
    void opcionesDeMorososParaGerenciaIncluyenGraficasSeriesYFiltros() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS/opciones-visualizacion").session(sesion(NombreRol.GERENCIA, "g_op_mor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoReporte").value("MOROSOS"))
                .andExpect(jsonPath("$.puedePersonalizar").value(true))
                .andExpect(jsonPath("$.tiposGrafica", contains("TABLA", "BARRAS", "LINEAS", "CIRCULAR")))
                .andExpect(jsonPath("$.series[*].id", contains("deudaVencida", "montoSinBeneficio", "montoConBeneficio")))
                .andExpect(jsonPath("$.series[0].formato").value("MONTO"))
                .andExpect(jsonPath("$.porDefecto.tipoGrafica").value("TABLA"))
                .andExpect(jsonPath("$.porDefecto.serie").value("deudaVencida"))
                .andExpect(jsonPath("$.filtros[*].nombre", contains("anioDesde", "anioHasta", "zona", "tributo",
                        "regimen", "tipoDocumento", "etapa", "beneficio", "montoMinimo")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'anioHasta')].tipo", contains("ANIO")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'anioHasta')].minimo", contains(1900)))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'anioHasta')].maximo", contains(2026)))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'zona')].valores[*]", contains("Costanera", "Maranga", "Pando")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'tributo')].valores[*]",
                        contains("IMPUESTO_PREDIAL", "ARBITRIOS")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'beneficio')].valores[*]",
                        contains("AMBOS", "CON_BENEFICIO", "SIN_BENEFICIO")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'montoMinimo')].minimo", contains(0)));
    }

    @Test
    void opcionesDePricosYBuenosPagadoresNoOfrecenLineas() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "g_op_otros");

        mvc.perform(get("/api/reportes/PRICOS/opciones-visualizacion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposGrafica", contains("TABLA", "BARRAS", "CIRCULAR")))
                .andExpect(jsonPath("$.series[*].id",
                        contains("baseImponible", "emisionTotal", "deudaAnterior", "deudaActual")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'criterio')].valores[*]", hasItem("BASE_IMPONIBLE")))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'limite')].minimo", contains(1)))
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'limite')].maximo", contains(200)));

        mvc.perform(get("/api/reportes/BUENOS_PAGADORES/opciones-visualizacion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposGrafica", contains("TABLA", "BARRAS", "CIRCULAR")))
                .andExpect(jsonPath("$.porDefecto.serie").value("totalPagado"))
                .andExpect(jsonPath("$.series[?(@.id == 'cuotasPagadasATiempo')].formato", contains("CANTIDAD")));
    }

    @Test
    void sinPermisoDePersonalizarSoloSeOfreceLaTabla() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS/opciones-visualizacion")
                        .session(sesion(NombreRol.ANALISTA_RENTAS, "a_op_tabla")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedePersonalizar").value(false))
                .andExpect(jsonPath("$.tiposGrafica", contains("TABLA")));
    }

    @Test
    void lasZonasOfrecidasRespetanLaRestriccionDelRol() throws Exception {
        restringir(NombreRol.GERENCIA, TipoReporte.MOROSOS, DimensionRestringida.ZONA, "Maranga");

        mvc.perform(get("/api/reportes/MOROSOS/opciones-visualizacion").session(sesion(NombreRol.GERENCIA, "g_op_zona")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros[?(@.nombre == 'zona')].valores[*]", contains("Costanera", "Pando")));
    }

    @Test
    void opcionesDeUnReporteRestringidoDevuelven403() throws Exception {
        restringir(NombreRol.ANALISTA_RENTAS, TipoReporte.PRICOS, DimensionRestringida.REPORTE, null);

        mvc.perform(get("/api/reportes/PRICOS/opciones-visualizacion")
                        .session(sesion(NombreRol.ANALISTA_RENTAS, "a_op_restr")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("REPORTE_RESTRINGIDO"));
    }

    @Test
    void opcionesSinSesionDevuelven401YRolNoAutorizado403() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS/opciones-visualizacion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reportes/MOROSOS/opciones-visualizacion")
                        .session(sesion(NombreRol.VENTANILLA, "v_op")))
                .andExpect(status().isForbidden());
    }

    /* ===== Validación de la personalización al guardar ===== */

    @Test
    void guardarConGraficaYSeriePermitidasConservaLaPersonalizacion() throws Exception {
        guardar("MOROSOS", sesion(NombreRol.GERENCIA, "g_ok"), """
                {
                  "nombre": "Morosos en líneas",
                  "tipoGrafica": "LINEAS",
                  "filtros": { "anioDesde": 2024, "anioHasta": 2026, "zona": "Costanera", "beneficio": "SIN_BENEFICIO" },
                  "columnasVisibles": ["codigo", "nombre", "deudaSinBeneficio"],
                  "series": ["montoSinBeneficio", "montoSinBeneficio"]
                }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoGrafica").value("LINEAS"))
                .andExpect(jsonPath("$.series", contains("montoSinBeneficio")))
                .andExpect(jsonPath("$.filtros.beneficio").value("SIN_BENEFICIO"));
    }

    @Test
    void graficaSinSerieUsaLaSeriePorDefecto() throws Exception {
        guardar("BUENOS_PAGADORES", sesion(NombreRol.GERENCIA, "g_defecto"), """
                {
                  "nombre": "Buenos pagadores en barras",
                  "tipoGrafica": "BARRAS",
                  "columnasVisibles": ["codigo", "nombre"]
                }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.series", contains("totalPagado")));
    }

    @Test
    void graficaNoDisponibleParaElReporteDevuelve400() throws Exception {
        guardar("PRICOS", sesion(NombreRol.GERENCIA, "g_graf"), """
                { "nombre": "PRICOS en líneas", "tipoGrafica": "LINEAS", "columnasVisibles": ["codigo", "nombre"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("GRAFICA_NO_PERMITIDA"));
    }

    @Test
    void serieNoDisponibleParaElReporteDevuelve400() throws Exception {
        guardar("MOROSOS", sesion(NombreRol.GERENCIA, "g_serie"), """
                {
                  "nombre": "Serie ajena",
                  "tipoGrafica": "BARRAS",
                  "columnasVisibles": ["codigo", "nombre"],
                  "series": ["baseImponible"]
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SERIE_NO_PERMITIDA"));
    }

    @Test
    void sinPermisoUnaGraficaDevuelve403PeroLaTablaSeGuarda() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.ANALISTA_RENTAS, "a_graf");

        guardar("MOROSOS", sesion, """
                { "nombre": "Barras", "tipoGrafica": "BARRAS", "columnasVisibles": ["codigo", "nombre"] }
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERSONALIZACION_NO_PERMITIDA"));

        guardar("MOROSOS", sesion, """
                {
                  "nombre": "Tabla",
                  "tipoGrafica": "TABLA",
                  "columnasVisibles": ["codigo", "nombre"],
                  "series": ["deudaVencida"]
                }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoGrafica").value("TABLA"));
    }

    @Test
    void filtrosFueraDeRangoONoAplicablesDevuelven400() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "g_filtros");

        guardar("PRICOS", sesion, """
                { "nombre": "Futuro", "filtros": { "anio": 2099 }, "columnasVisibles": ["codigo"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ANIO_FUERA_DE_RANGO"));

        guardar("PRICOS", sesion, """
                { "nombre": "Régimen", "filtros": { "regimen": "GENERAL" }, "columnasVisibles": ["codigo"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTRO_NO_APLICABLE"));

        guardar("MOROSOS", sesion, """
                { "nombre": "Criterio", "filtros": { "criterio": "BASE_IMPONIBLE" }, "columnasVisibles": ["codigo"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTRO_NO_APLICABLE"));

        guardar("BUENOS_PAGADORES", sesion, """
                { "nombre": "Negativo", "filtros": { "montoMinimo": -5 }, "columnasVisibles": ["codigo"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MONTO_INVALIDO"));
    }

    @Test
    void zonaRestringidaAlRolDevuelve403() throws Exception {
        restringir(NombreRol.GERENCIA, TipoReporte.MOROSOS, DimensionRestringida.ZONA, "Maranga");

        guardar("MOROSOS", sesion(NombreRol.GERENCIA, "g_zona"), """
                { "nombre": "Zona 2", "filtros": { "zona": "Maranga" }, "columnasVisibles": ["codigo"] }
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ZONA_RESTRINGIDA"));
    }

    /* ===== Validación al actualizar y vistas antiguas ===== */

    @Test
    void actualizarValidaLaGraficaConLasSeriesGuardadas() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "g_act");
        Number idVista = idDe(guardar("MOROSOS", sesion, """
                {
                  "nombre": "Para actualizar",
                  "tipoGrafica": "BARRAS",
                  "columnasVisibles": ["codigo", "nombre"],
                  "series": ["montoConBeneficio"]
                }
                """));

        actualizar(idVista, sesion, """
                { "tipoGrafica": "CIRCULAR" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoGrafica").value("CIRCULAR"))
                .andExpect(jsonPath("$.series", contains("montoConBeneficio")));

        actualizar(idVista, sesion, """
                { "series": ["totalPagado"] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SERIE_NO_PERMITIDA"));

        actualizar(idVista, sesion, """
                { "filtros": { "anioDesde": 1800, "anioHasta": 2025 } }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ANIO_FUERA_DE_RANGO"));
    }

    @Test
    void sinPermisoNoSePuedeCambiarLaVistaAUnaGrafica() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.ANALISTA_RENTAS, "a_act");
        Number idVista = idDe(guardar("PRICOS", sesion, """
                { "nombre": "Tabla PRICOS", "columnasVisibles": ["codigo", "nombre"] }
                """));

        actualizar(idVista, sesion, """
                { "tipoGrafica": "BARRAS" }
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERSONALIZACION_NO_PERMITIDA"));
    }

    @Test
    void lasSeriesAntiguasNoDisponiblesSeIgnoranAlLeerLaVista() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA, "g_antigua");
        Number idVista = idDe(guardar("MOROSOS", sesion, """
                { "nombre": "Antigua", "tipoGrafica": "BARRAS", "columnasVisibles": ["codigo", "nombre"] }
                """));
        jdbc.update("UPDATE operativo.vista_reporte SET series = ? WHERE id_vista = ?",
                "[\"serieRetirada\",\"deudaVencida\"]", idVista.longValue());

        mvc.perform(get("/api/reportes/vistas/" + idVista).session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series", contains("deudaVencida")))
                .andExpect(jsonPath("$.series", not(hasItem("serieRetirada"))));

        actualizar(idVista, sesion, """
                { "tipoGrafica": "CIRCULAR" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series", contains("deudaVencida")));
    }

    private ResultActions guardar(String tipo, MockHttpSession sesion, String cuerpo) throws Exception {
        return mvc.perform(post("/api/reportes/" + tipo + "/vistas").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).session(sesion));
    }

    private ResultActions actualizar(Number idVista, MockHttpSession sesion, String cuerpo) throws Exception {
        return mvc.perform(put("/api/reportes/vistas/" + idVista).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).session(sesion));
    }

    private static Number idDe(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.read(cuerpo, "$.idVista");
    }

    private void restringir(NombreRol rol, TipoReporte tipo, DimensionRestringida dimension, String valor) {
        restricciones.save(new RestriccionReporte(roles.findByNombre(rol).orElseThrow(), tipo, dimension, valor,
                false));
    }

    private MockHttpSession sesion(NombreRol rol, String username) {
        Usuario usuario = usuarios.save(new Usuario(username, "Usuario " + username, username + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
