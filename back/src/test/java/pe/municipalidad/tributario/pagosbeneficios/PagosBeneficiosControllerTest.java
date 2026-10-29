package pe.municipalidad.tributario.pagosbeneficios;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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

/* HU048: pagos y beneficios de un contribuyente para Ventanilla y Rentas. Fecha de corte 2026-10-19. */
@PruebaIntegracion
@Transactional
class PagosBeneficiosControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private SamnetGateway samnet;

    @Test
    void buscaPorCodigoYMuestraFechaCanalMontoYBeneficio() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "021390")
                        .session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("021390"))
                .andExpect(jsonPath("$.contribuyente.tipoDocumento").value("RUC"))
                .andExpect(jsonPath("$.contribuyente.numeroDocumento").value("20456781239"))
                .andExpect(jsonPath("$.contribuyente.nombre").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.pagos", hasSize(3)))
                .andExpect(jsonPath("$.pagos[0].idPago").value("REC-021390-2025-3"))
                .andExpect(jsonPath("$.pagos[0].fechaPago").value("2025-09-25"))
                .andExpect(jsonPath("$.pagos[0].canal").value("VENTANILLA"))
                .andExpect(jsonPath("$.pagos[0].montoPagado").value(2217.28))
                .andExpect(jsonPath("$.pagos[0].montoCorrespondiente").value(2217.28))
                .andExpect(jsonPath("$.pagos[0].diferencia").value(0))
                .andExpect(jsonPath("$.pagos[0].beneficioAplicado").value(nullValue()))
                .andExpect(jsonPath("$.pagos[1].canal").value("EN_LINEA"))
                .andExpect(jsonPath("$.resumen.cantidadPagos").value(3))
                .andExpect(jsonPath("$.resumen.pagosConBeneficio").value(0))
                .andExpect(jsonPath("$.resumen.totalPagado").value(6523.92));
    }

    @Test
    void tambienBuscaPorDocumento() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", " 20456781239 ")
                        .session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.codigo").value("021390"))
                .andExpect(jsonPath("$.pagos", hasSize(3)));
    }

    @Test
    void incluyeElEstadoDeCuentaDeSamnet() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "021390")
                        .session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta.totalEmitido").value(13431.60))
                .andExpect(jsonPath("$.estadoCuenta.totalPagado").value(6523.92))
                .andExpect(jsonPath("$.estadoCuenta.totalDeuda").value(6907.68))
                .andExpect(jsonPath("$.estadoCuenta.obligacionesVencidas").value(6));
    }

    @Test
    void conBeneficioMuestraLoQueCorrespondiaYLaDiferencia() throws Exception {
        jdbc.update("UPDATE samnet.pago SET id_beneficio = 'BEN-2026-0001', monto_correspondiente = monto_pagado + 100"
                + " WHERE id_pago = (SELECT min(id_pago) FROM samnet.pago WHERE contribuyente_codigo = '033218')");

        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "033218")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.pagosConBeneficio").value(1))
                .andExpect(jsonPath("$.resumen.totalDiferencia").value(-100.0))
                .andExpect(jsonPath("$.pagos[?(@.beneficioAplicado == 'BEN-2026-0001')].diferencia").value(-100.0))
                .andExpect(jsonPath("$.beneficiosVigentes", hasSize(1)))
                .andExpect(jsonPath("$.beneficiosVigentes[0].idBeneficio").value("BEN-2026-0001"))
                .andExpect(jsonPath("$.beneficiosVigentes[0].tipo").value("PARTICULAR"))
                .andExpect(jsonPath("$.beneficiosVigentes[0].porcentajeDescuento").value(50.0));
    }

    @Test
    void sinPagosLoIndicaYAunAsiMuestraElEstadoDeCuenta() throws Exception {
        jdbc.update("DELETE FROM samnet.pago_obligacion WHERE id_pago IN"
                + " (SELECT id_pago FROM samnet.pago WHERE contribuyente_codigo = '014502')");
        jdbc.update("DELETE FROM samnet.pago WHERE contribuyente_codigo = '014502'");

        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "014502")
                        .session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagos", hasSize(0)))
                .andExpect(jsonPath("$.resumen.cantidadPagos").value(0))
                .andExpect(jsonPath("$.resumen.totalPagado").value(0))
                .andExpect(jsonPath("$.estadoCuenta.totalEmitido", greaterThan(0.0)))
                .andExpect(jsonPath("$.beneficiosVigentes", hasSize(0)));
    }

    @Test
    void contribuyenteInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "999999")
                        .session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
    }

    @Test
    void sinConsultaDevuelve400() throws Exception {
        MockHttpSession ventanilla = sesion(NombreRol.VENTANILLA);
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").session(ventanilla))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "   ").session(ventanilla))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinSesionInternaDevuelve401() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "021390"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conUnRolSinPermisoDevuelve403() throws Exception {
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "021390")
                        .session(sesion(NombreRol.TERMINALISTA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unContribuyenteNoPuedeConsultarPagosDeOtros() throws Exception {
        MockHttpSession contribuyente = new MockHttpSession();
        contribuyente.setAttribute("citizenCode", "021390");
        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "014502").session(contribuyente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void siSamnetNoRespondeInformaQueNoEstaDisponible() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerEstadoCuenta(anyString());

        mvc.perform(get("/api/contribuyentes/pagos-beneficios").param("consulta", "021390")
                        .session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
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
