package pe.municipalidad.tributario.declaracionjurada;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetNoDisponibleException;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class DeclaracionJuradaControllerTest {

    private static final String RUTA = "/api/predios/{codigo}/declaracion-jurada";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;

    @MockitoSpyBean
    private SamnetGateway samnet;

    @Test
    void devuelveLaIdentificacionDelPredioLaVigenteYSuHistorial() throws Exception {
        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.codigoPredio").value("014502-01"))
                .andExpect(jsonPath("$.predio.numeroCatastral").value("150136-001-014"))
                .andExpect(jsonPath("$.predio.ubicacion").value("Av. La Marina 1450, San Miguel"))
                .andExpect(jsonPath("$.declaracionVigente.idDeclaracion").value("DJ-2024-001450"))
                .andExpect(jsonPath("$.declaracionVigente.anio").value(2024))
                .andExpect(jsonPath("$.declaracionVigente.autoavaluo").value(185000.00))
                .andExpect(jsonPath("$.declaracionVigente.motivo").value("ACTUALIZACION"))
                .andExpect(jsonPath("$.declaracionVigente.submotivo").value("AMPLIACION"))
                .andExpect(jsonPath("$.declaracionVigente.tipoDeclarante").value("PROPIETARIO"))
                .andExpect(jsonPath("$.declaracionVigente.estado").value("PROCESADA"))
                .andExpect(jsonPath("$.declaracionVigente.esVigente").value(true))
                .andExpect(jsonPath("$.historial", hasSize(1)))
                .andExpect(jsonPath("$.historial[0].idDeclaracion").value("DJ-2012-000141"))
                .andExpect(jsonPath("$.historial[0].anio").value(2012))
                .andExpect(jsonPath("$.historial[0].autoavaluo").value(150000.00))
                .andExpect(jsonPath("$.historial[0].esVigente").value(false))
                .andExpect(jsonPath("$.fechaConsulta").value("2026-10-19"))
                .andExpect(jsonPath("$.advertencias", hasSize(0)));
    }

    @Test
    void predioSinDeclaracionesDevuelveVigenteNuloYAdvertencia() throws Exception {
        mockMvc.perform(get(RUTA, "099001-01").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.codigoPredio").value("099001-01"))
                .andExpect(jsonPath("$.predio.numeroCatastral").value("150136-009-001"))
                .andExpect(jsonPath("$.predio.ubicacion").value("Psje. Las Gaviotas 15, San Miguel"))
                .andExpect(jsonPath("$.declaracionVigente").value(nullValue()))
                .andExpect(jsonPath("$.historial", hasSize(0)))
                .andExpect(jsonPath("$.advertencias", hasSize(1)))
                .andExpect(jsonPath("$.advertencias[0].codigo").value("SIN_DECLARACIONES"));
    }

    @Test
    void historialSinNingunaVigenteDevuelveVigenteNuloYAdvertencia() throws Exception {
        DeclaracionJurada historica = new DeclaracionJurada("DJ-2012-000141", "014502-01", 2012,
                new BigDecimal("150000.00"), "INSCRIPCION", "COMPRA-VENTA", "PROPIETARIO", "PROCESADA", false);
        doReturn(List.of(historica)).when(samnet).obtenerDeclaracionJurada("014502-01");

        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaracionVigente").value(nullValue()))
                .andExpect(jsonPath("$.historial", hasSize(1)))
                .andExpect(jsonPath("$.historial[0].idDeclaracion").value("DJ-2012-000141"))
                .andExpect(jsonPath("$.advertencias", hasSize(1)))
                .andExpect(jsonPath("$.advertencias[0].codigo").value("SIN_DECLARACION_VIGENTE"));
    }

    @Test
    void masDeUnaVigenteDevuelve409() throws Exception {
        DeclaracionJurada primera = new DeclaracionJurada("DJ-2024-001450", "014502-01", 2024,
                new BigDecimal("185000.00"), "ACTUALIZACION", "AMPLIACION", "PROPIETARIO", "PROCESADA", true);
        DeclaracionJurada segunda = new DeclaracionJurada("DJ-2023-000900", "014502-01", 2023,
                new BigDecimal("170000.00"), "ACTUALIZACION", "AMPLIACION", "PROPIETARIO", "PROCESADA", true);
        doReturn(List.of(primera, segunda)).when(samnet).obtenerDeclaracionJurada("014502-01");

        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DECLARACION_VIGENTE_AMBIGUA"));
    }

    @Test
    void predioInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(RUTA, "999999-99").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PREDIO_NO_ENCONTRADO"));
    }

    @Test
    void codigoEnBlancoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA, " ").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_PREDIO_INVALIDO"));
    }

    @Test
    void codigoDemasiadoLargoDevuelve400() throws Exception {
        mockMvc.perform(get(RUTA, "1234567890123").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_PREDIO_INVALIDO"));
    }

    @Test
    void samnetNoDisponibleDevuelve503() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).obtenerDeclaracionJurada("014502-01");

        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SAMNET_NO_DISPONIBLE"));
    }

    @Test
    void sinSesionInternaDevuelve401() throws Exception {
        mockMvc.perform(get(RUTA, "014502-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
    }

    @Test
    void conUnRolSinPermisoDevuelve403() throws Exception {
        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ERROR_HTTP"));
    }

    @Test
    void elAdministradorTambienPuedeConsultar() throws Exception {
        mockMvc.perform(get(RUTA, "014502-01").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.codigoPredio").value("014502-01"));
    }

    @Test
    void unContribuyenteNoPuedeConsultarLaDeclaracionJurada() throws Exception {
        MockHttpSession contribuyente = new MockHttpSession();
        contribuyente.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, "014502");

        mockMvc.perform(get(RUTA, "014502-01").session(contribuyente))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
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
