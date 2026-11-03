package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU037: reporte PRICOS con los datos de prueba y fecha de corte 2026-10-19. Muestra de control de la fila de
 * 021390 (INVERSIONES COSTA AZUL, predio de base imponible 960 000,00):
 *
 *   Emisión 2026      predial 1010,88 + 1029,60 = 2040,48   arbitrios 1291,68 + 1315,60 = 2607,28
 *   Deuda anteriores  predial 992,16                         arbitrios 1267,76  (cuota 4 de 2025 sin pagar)
 *   Deuda 2026        predial 2040,48                        arbitrios 2607,28  (todas vencidas y sin pagar)
 *   Deuda total       6907,68, igual al total de deuda de HU048 para ese contribuyente
 *
 * Por base imponible el orden es 045067 (1 250 000), 021390 (960 000), 014502 (185 000) y 033218 (142 000).
 */
@PruebaIntegracion
@Transactional
class ReportePricosControllerTest {

    private static final String RUTA = "/api/reportes/pricos";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private RolRepository roles;

    @Autowired
    private ClaveInternaService claves;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private RestriccionReporteRepository restricciones;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private SamnetGateway samnet;

    @Test
    void sinSesionInternaDevuelve401YConUnRolSinPermisoDevuelve403() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
        mockMvc.perform(get(RUTA).session(sesion(NombreRol.TERMINALISTA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void muestraLasColumnasDeRf050DeCadaContribuyente() throws Exception {
        LoteETL lote = cargaPublicada();

        mockMvc.perform(get(RUTA).param("limite", "2").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.criterio").value("BASE_IMPONIBLE"))
                .andExpect(jsonPath("$.filas[1].posicion").value(2))
                .andExpect(jsonPath("$.filas[1].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[1].razonSocial").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.filas[1].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[1].documento.tipo").value("RUC"))
                .andExpect(jsonPath("$.filas[1].documento.numero").value("20456781239"))
                .andExpect(jsonPath("$.filas[1].domicilioFiscal.direccion").value("Jr. Los Precursores 220, San Miguel"))
                .andExpect(jsonPath("$.filas[1].domicilioFiscal.via").value("Jr. Los Precursores"))
                .andExpect(jsonPath("$.filas[1].domicilioFiscal.numero").value("220"))
                .andExpect(jsonPath("$.filas[1].domicilioFiscal.distrito").value("SAN MIGUEL"))
                .andExpect(jsonPath("$.filas[1].baseImponible").value(960000.0))
                .andExpect(jsonPath("$.filas[1].emisionAnioCorte.predial").value(2040.48))
                .andExpect(jsonPath("$.filas[1].emisionAnioCorte.arbitrios").value(2607.28))
                .andExpect(jsonPath("$.filas[1].deudaAniosAnteriores.predial").value(992.16))
                .andExpect(jsonPath("$.filas[1].deudaAniosAnteriores.arbitrios").value(1267.76))
                .andExpect(jsonPath("$.filas[1].deudaAnioCorte.predial").value(2040.48))
                .andExpect(jsonPath("$.filas[1].deudaAnioCorte.arbitrios").value(2607.28))
                .andExpect(jsonPath("$.filas[1].deudaAnioCorte.porcentajeDescuentoArbitrios").value(0.0))
                .andExpect(jsonPath("$.filas[1].condicion").value("HABIDO"))
                .andExpect(jsonPath("$.filas[1].tipoPersona").value("JURIDICA"))
                .andExpect(jsonPath("$.filas[1].correo").value("cobranzas@costaazul.test"))
                .andExpect(jsonPath("$.filas[1].telefonos").value("014500202"));
    }

    @Test
    void porBaseImponibleLosMasGrandesVanPrimeroYElLimiteRecortaLaLista() throws Exception {
        cargaPublicada();

        mockMvc.perform(get(RUTA).param("limite", "4").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(4))
                .andExpect(jsonPath("$.totalContribuyentes", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.filas", hasSize(4)))
                .andExpect(jsonPath("$.filas[0].codigo").value("045067"))
                .andExpect(jsonPath("$.filas[0].baseImponible").value(1250000.0))
                .andExpect(jsonPath("$.filas[1].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[2].codigo").value("014502"))
                .andExpect(jsonPath("$.filas[3].codigo").value("033218"));
    }

    @Test
    void porDeudaTotalElMayorDeudorVaPrimero() throws Exception {
        cargaPublicada();

        mockMvc.perform(get(RUTA).param("criterio", "DEUDA_TOTAL").param("limite", "1")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("DEUDA_TOTAL"))
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].codigo").value("021390"));
    }

    @Test
    void laFechaDeCorteEsLaDeLaUltimaCargaPublicada() throws Exception {
        cargaPublicada();

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"));
    }

    @Test
    void cadaGeneracionQuedaRegistradaComoSolicitudDeReporte() throws Exception {
        cargaPublicada();
        int antes = solicitudesPricos();

        mockMvc.perform(get(RUTA).param("criterio", "DEUDA_TOTAL").param("limite", "3")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk());

        assertThat(solicitudesPricos()).isEqualTo(antes + 1);
        assertThat(jdbc.queryForObject("""
                SELECT filtros_aplicados FROM operativo.solicitud_reporte
                WHERE tipo_reporte = 'PRICOS' ORDER BY id_solicitud DESC LIMIT 1
                """, String.class)).isEqualTo("{\"criterio\":\"DEUDA_TOTAL\",\"limite\":3}");
    }

    @Test
    void unRolConElReporteRestringidoNoLoPuedeGenerar() throws Exception {
        cargaPublicada();
        Rol gerencia = roles.findByNombre(NombreRol.GERENCIA).orElseThrow();
        restricciones.saveAndFlush(new RestriccionReporte(gerencia, TipoReporte.PRICOS,
                DimensionRestringida.REPORTE, null, false));

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unLimiteOUnCriterioInvalidosDevuelven400() throws Exception {
        MockHttpSession sesion = sesion(NombreRol.GERENCIA);

        mockMvc.perform(get(RUTA).param("limite", "0").session(sesion))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).param("limite", "201").session(sesion))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).param("criterio", "POR_NOMBRE").session(sesion))
                .andExpect(status().isBadRequest());
    }

    @Test
    void siSamnetNoRespondeInformaQueNoEstaDisponible() throws Exception {
        cargaPublicada();
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).extraerParaETL(anyString(), any());

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    private LoteETL cargaPublicada() {
        LoteETL lote = new LoteETL("ps_etl_cobranza", FECHA_CORTE, TipoEjecucion.PROGRAMADA, FECHA_CORTE.atTime(2, 0));
        lote.publicar(7, 7, FECHA_CORTE.atTime(2, 5));
        return lotes.saveAndFlush(lote);
    }

    private int solicitudesPricos() {
        return jdbc.queryForObject("SELECT count(*) FROM operativo.solicitud_reporte WHERE tipo_reporte = 'PRICOS'",
                Integer.class);
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
