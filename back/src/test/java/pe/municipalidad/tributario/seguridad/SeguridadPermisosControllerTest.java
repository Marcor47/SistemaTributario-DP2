package pe.municipalidad.tributario.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class SeguridadPermisosControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired ClaveInternaService claves;

    @Test
    void aplicaLaMatrizDePermisosDeLosSeisRoles() throws Exception {
        MockHttpSession admin = sesion(NombreRol.ADMINISTRADOR);
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);
        MockHttpSession analista = sesion(NombreRol.ANALISTA_RENTAS);
        MockHttpSession datos = sesion(NombreRol.RESPONSABLE_DATOS);
        MockHttpSession ventanilla = sesion(NombreRol.VENTANILLA);
        MockHttpSession terminalista = sesion(NombreRol.TERMINALISTA);

        mvc.perform(get("/api/admin/usuarios").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/contribuyentes").session(admin).param("tipoDocumento", "DNI")
                .param("numeroDocumento", "41028765")).andExpect(status().isForbidden());
        mvc.perform(get("/api/metas").session(gerencia).param("anio", "2026"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/usuarios").session(gerencia)).andExpect(status().isForbidden());
        mvc.perform(get("/api/contribuyentes").session(analista).param("tipoDocumento", "DNI")
                .param("numeroDocumento", "41028765")).andExpect(status().isOk());
        mvc.perform(get("/api/etl/calidad/reglas").session(datos)).andExpect(status().isOk());
        mvc.perform(get("/api/formularios").session(ventanilla)).andExpect(status().isOk());
        mvc.perform(get("/api/formularios").session(terminalista)).andExpect(status().isOk());
        mvc.perform(get("/api/metas").session(ventanilla)).andExpect(status().isForbidden());
    }

    @Test
    void obligaACambiarLaClaveTemporalAntesDeUsarModulos() throws Exception {
        String temporal = "TemporalSM#2026";
        Usuario usuario = usuarios.save(new Usuario("temporal_hu034", "Temporal HU034",
                "temporal-hu034@example.org", claves.hashear(temporal), rol(NombreRol.VENTANILLA),
                LocalDateTime.now(), true));
        assertThat(usuario.getClaveHash()).startsWith("$2a$12$").isNotEqualTo(temporal);

        var login = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"temporal_hu034\",\"clave\":\"TemporalSM#2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cambioClavePendiente").value(true))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/formularios").session(sesion)).andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/interno/cambiar-clave").session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveActual\":\"TemporalSM#2026\",\"claveNueva\":\"PersonalSM#2026\"}"))
                .andExpect(status().isNoContent());
        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"temporal_hu034\",\"clave\":\"TemporalSM#2026\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"temporal_hu034\",\"clave\":\"PersonalSM#2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void conservaLaSesionAlConsultarElUsuarioDespuesDelLogin() throws Exception {
        usuarios.save(new Usuario("sesion_hu034", "Sesión HU034", "sesion-hu034@example.org",
                claves.hashear("SesionSM#2026"), rol(NombreRol.VENTANILLA), LocalDateTime.now()));

        var login = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"sesion_hu034\",\"clave\":\"SesionSM#2026\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/auth/interno/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("sesion_hu034"))
                .andExpect(jsonPath("$.rol").value("VENTANILLA"));
        assertThat(sesion.isInvalid()).isFalse();
    }

    @Test
    void rechazaUnaClaveQueSuperaElLimiteDeBcrypt() throws Exception {
        String clave = "á".repeat(37);
        mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"usuario\",\"clave\":\"" + clave + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession sesion(NombreRol nombreRol) {
        String sufijo = nombreRol.name().toLowerCase();
        Usuario usuario = usuarios.save(new Usuario("prueba_" + sufijo, "Prueba " + nombreRol,
                sufijo + "@example.org", claves.hashear("PruebaSM#2026"), rol(nombreRol),
                LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }

    private Rol rol(NombreRol nombre) {
        return roles.findByNombre(nombre).orElseThrow();
    }
}
