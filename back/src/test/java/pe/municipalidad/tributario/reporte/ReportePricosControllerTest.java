package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
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
 * 021390 (INVERSIONES COSTA AZUL, 11 predios con base imponible total 4 270 000,00):
 *
 *   Emisión 2026      predial 2040,48   arbitrios 12 707,28
 *   Deuda anteriores  predial 992,16    arbitrios 1267,76  (cuota 4 de 2025 sin pagar)
 *   Deuda 2026        predial 2040,48   arbitrios 12 707,28 (todas vencidas y sin pagar)
 *   Deuda total       6907,68 de predial + 13 975,04 de arbitrios
 *
 * Por base imponible el orden es 021390 (4 270 000), 045067 (1 250 000), 058934/061177 (210 000) y 014502 (185 000).
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
        mockMvc.perform(get(RUTA).session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void muestraLasColumnasDeRf050DeCadaContribuyente() throws Exception {
        cargaPublicada();
        Long idLoteSeed = jdbc.queryForObject("""
                SELECT id_lote FROM analitica.lote_etl
                WHERE fuente = 'ps_etl_cobranza' AND fecha_corte = DATE '2026-10-19'
                ORDER BY id_lote LIMIT 1
                """, Long.class);

        mockMvc.perform(get(RUTA).param("limite", "2").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(idLoteSeed))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.criterio").value("BASE_IMPONIBLE"))
                .andExpect(jsonPath("$.filas[0].posicion").value(1))
                .andExpect(jsonPath("$.filas[0].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[0].razonSocial").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.filas[0].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[0].documento.tipo").value("RUC"))
                .andExpect(jsonPath("$.filas[0].documento.numero").value("20456781239"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal.direccion").value("Jr. Los Precursores 220, San Miguel"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal.via").value("Jr. Los Precursores"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal.numero").value("220"))
                .andExpect(jsonPath("$.filas[0].domicilioFiscal.distrito").value("SAN MIGUEL"))
                .andExpect(jsonPath("$.filas[0].baseImponible").value(4270000.0))
                .andExpect(jsonPath("$.filas[0].emisionAnioCorte.predial").value(2040.48))
                .andExpect(jsonPath("$.filas[0].emisionAnioCorte.arbitrios").value(12707.28))
                .andExpect(jsonPath("$.filas[0].deudaAniosAnteriores.predial").value(992.16))
                .andExpect(jsonPath("$.filas[0].deudaAniosAnteriores.arbitrios").value(1267.76))
                .andExpect(jsonPath("$.filas[0].deudaAnioCorte.predial").value(2040.48))
                .andExpect(jsonPath("$.filas[0].deudaAnioCorte.arbitrios").value(12707.28))
                .andExpect(jsonPath("$.filas[0].deudaAnioCorte.porcentajeDescuentoArbitrios").value(0.0))
                .andExpect(jsonPath("$.filas[0].condicion").value("HABIDO"))
                .andExpect(jsonPath("$.filas[0].tipoPersona").value("JURIDICA"))
                .andExpect(jsonPath("$.filas[0].correo").value("cobranzas@costaazul.test"))
                .andExpect(jsonPath("$.filas[0].telefonos").value("014500202"));
    }

    @Test
    void porBaseImponibleLosMasGrandesVanPrimeroYElLimiteRecortaLaLista() throws Exception {
        cargaPublicada();

        mockMvc.perform(get(RUTA).param("limite", "4").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(4))
                .andExpect(jsonPath("$.totalContribuyentes", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.filas", hasSize(4)))
                .andExpect(jsonPath("$.filas[0].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[0].baseImponible").value(4270000.0))
                .andExpect(jsonPath("$.filas[1].codigo").value("045067"))
                .andExpect(jsonPath("$.filas[1].baseImponible").value(1250000.0))
                .andExpect(jsonPath("$.filas[2].codigo").value("058934"))
                .andExpect(jsonPath("$.filas[3].codigo").value("061177"));
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

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
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
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).buscarContribuyente(anyString());

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    @Test
    void sinCargaPublicadaConHechosDevuelveErrorDeNegocio() throws Exception {
        jdbc.update("DELETE FROM analitica.hecho_cobranza");

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_CARGA_ETL"));
    }

    @Test
    void sinCorrespondenciaConSamnetDevuelveErrorDeNegocio() throws Exception {
        LoteETL lote = cargaPublicada();
        String seudonimo = "pricos-sin-correspondencia";
        jdbc.update("""
                INSERT INTO analitica.dim_contribuyente
                    (seudonimo_contribuyente, tipo_persona, regimen, condicion)
                VALUES (?, 'JURIDICA', 'GENERAL', 'HABIDO')
                """, seudonimo);
        Long idPredio = jdbc.queryForObject("""
                INSERT INTO analitica.dim_predio
                    (seudonimo_predio, zona, uso_predio, tipo_predio, base_imponible)
                VALUES ('pricos-predio-sin-correspondencia', 'Z1', 'Comercio', 'URBANO', 99999999.00)
                RETURNING id_predio
                """, Long.class);
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza
                    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
                     monto_emitido, monto_insoluto, monto_pagado, monto_descuento, estado_pago)
                VALUES (?, ?, ?, 'IMPUESTO_PREDIAL', 2026, 1, DATE '2026-03-31',
                        100.00, 100.00, 0, 0, 'VENCIDA')
                """, lote.getIdLote(), seudonimo, idPredio);

        mockMvc.perform(get(RUTA).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ETL_SIN_CORRESPONDENCIA"));
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
