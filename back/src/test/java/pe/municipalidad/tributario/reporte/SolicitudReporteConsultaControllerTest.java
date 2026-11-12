package pe.municipalidad.tributario.reporte;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class SolicitudReporteConsultaControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SolicitudReporteRepository solicitudes;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;

    @Test
    void gerenciaConsultaRegistroResumenYPeriodoInclusivo() throws Exception {
        Usuario gerencia = usuario("hu044.gerencia", NombreRol.GERENCIA);
        solicitudes.save(new SolicitudReporte(TipoReporte.MOROSOS, gerencia,
                LocalDateTime.of(2035, 4, 15, 23, 59), "zona=Maranga", FormatoExportacion.NINGUNO));
        solicitudes.save(new SolicitudReporte(TipoReporte.PRICOS, gerencia,
                LocalDateTime.of(2035, 4, 15, 10, 30), "criterio=DEUDA", FormatoExportacion.PDF));

        mvc.perform(get("/api/reportes/solicitudes")
                        .param("usuario", "HU044.GERENCIA")
                        .param("desde", "2035-04-15").param("hasta", "2035-04-15")
                        .param("pagina", "0").param("tamanio", "10").session(sesion(gerencia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.usuariosDistintos").value(1))
                .andExpect(jsonPath("$.solicitudes[*].tipoReporte", contains("MOROSOS", "PRICOS")))
                .andExpect(jsonPath("$.solicitudes[0].operacion").value("VISUALIZACION"))
                .andExpect(jsonPath("$.solicitudes[1].operacion").value("PDF"))
                .andExpect(jsonPath("$.frecuencias[0].cantidad").value(1))
                .andExpect(jsonPath("$.frecuencias[1].cantidad").value(1))
                .andExpect(jsonPath("$.frecuencias[2].cantidad").value(0));
    }

    @Test
    void analistaPuedeFiltrarPorTipo() throws Exception {
        Usuario analista = usuario("hu044.analista", NombreRol.ANALISTA_RENTAS);
        solicitudes.save(new SolicitudReporte(TipoReporte.BUENOS_PAGADORES, analista,
                LocalDateTime.of(2036, 1, 2, 8, 0), "{}", FormatoExportacion.EXCEL));

        mvc.perform(get("/api/reportes/solicitudes").param("usuario", "hu044.analista")
                        .param("tipo", "BUENOS_PAGADORES").session(sesion(analista)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.solicitudes[0].operacion").value("EXCEL"));
    }

    @Test
    void rechazaPeriodoIncompletoOInvertido() throws Exception {
        Usuario gerencia = usuario("hu044.fechas", NombreRol.GERENCIA);
        MockHttpSession sesion = sesion(gerencia);

        mvc.perform(get("/api/reportes/solicitudes").param("desde", "2035-04-15").session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INCOMPLETO"));
        mvc.perform(get("/api/reportes/solicitudes").param("desde", "2035-04-16")
                        .param("hasta", "2035-04-15").session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void ventanillaNoPuedeConsultarYUnaSesionAusenteRecibe401() throws Exception {
        Usuario ventanilla = usuario("hu044.ventanilla", NombreRol.VENTANILLA);
        mvc.perform(get("/api/reportes/solicitudes").session(sesion(ventanilla)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/solicitudes")).andExpect(status().isUnauthorized());
    }

    private Usuario usuario(String nombre, NombreRol rol) {
        return usuarios.save(new Usuario(nombre, "Usuario de prueba " + rol, nombre + "@example.org",
                claves.hashear("ClaveSegura#2026"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
    }

    private MockHttpSession sesion(Usuario usuario) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
