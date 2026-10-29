package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/* HU006: reportes de morosos, PRICOS y buenos pagadores sobre la BD sintética. Fecha de corte 2026-10-19. */
@PruebaIntegracion
@Transactional
class ReporteControllerTest {

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
    void elCatalogoTraeLosTresReportesConSusFiltros() throws Exception {
        mvc.perform(get("/api/reportes").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", contains("MOROSOS", "PRICOS", "BUENOS_PAGADORES")))
                .andExpect(jsonPath("$[0].titulo").value("Reporte de morosos"))
                .andExpect(jsonPath("$[0].filtros", contains("zona", "tributo", "montoMinimo")))
                .andExpect(jsonPath("$[1].filtros", contains("anio", "zona", "tributo", "montoMinimo")));
    }

    @Test
    void elCatalogoOcultaLosReportesRestringidosAlRol() throws Exception {
        restringir(NombreRol.GERENCIA, TipoReporte.PRICOS, DimensionRestringida.REPORTE, null);

        mvc.perform(get("/api/reportes").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", contains("MOROSOS", "BUENOS_PAGADORES")));
    }

    @Test
    void morososConSuDeudaVencidaDeTodosLosAniosYLaEtapaMasAvanzada() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoReporte").value("MOROSOS"))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.filtrosAplicados.anio").value(nullValue()))
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "045067", "058934", "061177")))
                .andExpect(jsonPath("$.filas[0].nombre").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.filas[0].tipoDocumento").value("RUC"))
                .andExpect(jsonPath("$.filas[0].numeroDocumento").value("20456781239"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal").value("Jr. Los Precursores 220, San Miguel"))
                .andExpect(jsonPath("$.filas[0].zonas", contains("Z2")))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(6907.68))
                .andExpect(jsonPath("$.filas[0].cuotasVencidas").value(6))
                .andExpect(jsonPath("$.filas[0].vencimientoMasAntiguo").value("2025-12-31"))
                .andExpect(jsonPath("$.filas[0].diasVencido").value(292))
                .andExpect(jsonPath("$.filas[0].etapaCobranza").value("COACTIVA"))
                .andExpect(jsonPath("$.filas[0].nombreEtapa").value("Exigible en vía coactiva"))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo[0].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo[0].monto").value(3875.04))
                .andExpect(jsonPath("$.filas[2].etapaCobranza").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.filas[3].etapaCobranza").value("MORA_TEMPRANA"))
                .andExpect(jsonPath("$.resumen.cantidad").value(4))
                .andExpect(jsonPath("$.resumen.conceptoMonto").value("Deuda vencida"))
                .andExpect(jsonPath("$.resumen.montoTotal").value(9420.16))
                .andExpect(jsonPath("$.resumen.umbralPrico").value(nullValue()));
    }

    @Test
    void morososPorZonaTributoYMontoMinimo() throws Exception {
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);
        mvc.perform(get("/api/reportes/MOROSOS").param("zona", " z2 ").param("tributo", "ARBITRIOS")
                        .session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtrosAplicados.zona").value("Z2"))
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "058934", "061177")))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(3875.04))
                .andExpect(jsonPath("$.filas[0].cuotasVencidas").value(3))
                .andExpect(jsonPath("$.filas[1].deudaVencida").value(401.12));

        mvc.perform(get("/api/reportes/MOROSOS").param("montoMinimo", "1000").session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "045067")));
    }

    @Test
    void pricosDelAnioConElUmbralConfigurado() throws Exception {
        mvc.perform(get("/api/reportes/PRICOS").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtrosAplicados.anio").value(2026))
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "045067")))
                .andExpect(jsonPath("$.filas[0].tipoPersona").value("JURIDICA"))
                .andExpect(jsonPath("$.filas[0].emitidoAnio").value(4647.76))
                .andExpect(jsonPath("$.filas[0].pagadoAnio").value(0))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(6907.68))
                .andExpect(jsonPath("$.filas[1].emitidoAnio").value(2145.12))
                .andExpect(jsonPath("$.resumen.conceptoMonto").value("Monto emitido en 2026"))
                .andExpect(jsonPath("$.resumen.montoTotal").value(6792.88))
                .andExpect(jsonPath("$.resumen.umbralPrico").value(2000));
    }

    @Test
    void pricosDeOtroAnio() throws Exception {
        mvc.perform(get("/api/reportes/PRICOS").param("anio", "2025").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390", "045067")))
                .andExpect(jsonPath("$.filas[0].emitidoAnio").value(8783.84))
                .andExpect(jsonPath("$.filas[0].pagadoAnio").value(6523.92))
                .andExpect(jsonPath("$.filas[0].porcentajePagado").value(74.27));
    }

    @Test
    void elUmbralDePricosSeConfiguraSinTocarCodigo() throws Exception {
        assertThat(jdbc.update("UPDATE seguridad.parametro_configuracion SET valor = '4000'"
                + " WHERE clave = 'UMBRAL_PRICO'")).isEqualTo(1);

        mvc.perform(get("/api/reportes/PRICOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("021390")))
                .andExpect(jsonPath("$.resumen.umbralPrico").value(4000));
    }

    @Test
    void buenosPagadoresSonLosQuePagaronTodoATiempoYNoDebenNada() throws Exception {
        mvc.perform(get("/api/reportes/BUENOS_PAGADORES").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("014502", "033218")))
                .andExpect(jsonPath("$.filas[0].cuotasPagadasATiempo").value(4))
                .andExpect(jsonPath("$.filas[0].pagadoAnio").value(893.80))
                .andExpect(jsonPath("$.filas[0].zonas", contains("Z1")))
                .andExpect(jsonPath("$.resumen.montoTotal").value(1385.40));
    }

    @Test
    void unPagoFueraDePlazoDejaDeSerBuenPagador() throws Exception {
        jdbc.update("UPDATE samnet.pago SET fecha_pago = DATE '2026-07-15' WHERE id_pago = 'REC-033218-2026-2'");

        mvc.perform(get("/api/reportes/BUENOS_PAGADORES").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("014502")));
    }

    @Test
    void laRestriccionPorZonaDelRolQuitaEsasFilas() throws Exception {
        restringir(NombreRol.GERENCIA, TipoReporte.MOROSOS, DimensionRestringida.ZONA, "Z2");

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].codigo", contains("045067")));
    }

    @Test
    void unReporteRestringidoAlRolDevuelve403() throws Exception {
        restringir(NombreRol.ANALISTA_RENTAS, TipoReporte.MOROSOS, DimensionRestringida.REPORTE, null);

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("REPORTE_RESTRINGIDO"));
    }

    @Test
    void cadaGeneracionQuedaRegistradaConSusFiltros() throws Exception {
        mvc.perform(get("/api/reportes/PRICOS").param("zona", "Z1").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk());

        List<String> filtros = jdbc.queryForList("SELECT s.filtros_aplicados FROM operativo.solicitud_reporte s"
                + " JOIN seguridad.usuario u ON u.id_usuario = s.id_usuario"
                + " WHERE u.nombre_usuario = 'usuario_gerencia' AND s.tipo_reporte = 'PRICOS'"
                + " AND s.formato_exportacion = 'NINGUNO'", String.class);
        assertThat(filtros).hasSize(1);
        assertThat(filtros.getFirst()).contains("\"anio\":2026").contains("\"zona\":\"Z1\"");
    }

    @Test
    void elAnioNoSeAplicaAMorosos() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS").param("anio", "2025").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTRO_NO_APLICABLE"));
    }

    @Test
    void filtrosInvalidosDevuelven400() throws Exception {
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);
        mvc.perform(get("/api/reportes/DEUDORES").session(gerencia))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/PRICOS").param("anio", "1999").session(gerencia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mvc.perform(get("/api/reportes/MOROSOS").param("tributo", "ALCABALA").session(gerencia))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/MOROSOS").param("montoMinimo", "-1").session(gerencia))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinSesionInternaDevuelve401() throws Exception {
        mvc.perform(get("/api/reportes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reportes/MOROSOS")).andExpect(status().isUnauthorized());
    }

    @Test
    void conUnRolSinPermisoDevuelve403() throws Exception {
        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void siSamnetNoRespondeInformaQueNoEstaDisponible() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerCartera(any(), anyInt());

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    private void restringir(NombreRol rol, TipoReporte tipo, DimensionRestringida dimension, String valor) {
        restricciones.save(new RestriccionReporte(roles.findByNombre(rol).orElseThrow(), tipo, dimension, valor,
                false));
    }

    private MockHttpSession sesion(NombreRol rol) {
        String nombre = "usuario_" + rol.name().toLowerCase();
        Usuario usuario = usuarios.save(new Usuario(nombre, "Usuario " + rol, nombre + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
