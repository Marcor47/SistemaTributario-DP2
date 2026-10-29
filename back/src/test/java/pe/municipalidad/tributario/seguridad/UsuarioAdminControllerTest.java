package pe.municipalidad.tributario.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class UsuarioAdminControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClaveInternaService claves;
    @Autowired EntityManager entityManager;

    private Usuario admin;

    @BeforeEach
    void preparar() {
        admin = usuarios.save(new Usuario("admin_hu034", "Administrador HU", "admin-hu034@example.org",
                claves.hashear("clave-segura"), rol(NombreRol.ADMINISTRADOR), LocalDateTime.now()));
    }

    @Test
    void creaUsuarioConHashYRegistrosVinculados() throws Exception {
        mvc.perform(post("/api/admin/usuarios").session(sesionAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombreUsuario":"operador_hu034","nombreCompleto":"Operador de rentas",
                         "correo":"operador-hu034@example.org","claveInicial":"clave-operador",
                         "rol":"VENTANILLA"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("VENTANILLA"));

        Usuario objetivo = usuarios.findByNombreUsuario("operador_hu034").orElseThrow();
        entityManager.flush();
        assertThat(objetivo.getClaveHash()).isNotEqualTo("clave-operador");
        assertThat(claves.verificar("clave-operador", objetivo.getClaveHash())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.usuario_rol WHERE id_usuario = ?
                """, Integer.class, objetivo.getIdUsuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.registro_auditoria
                WHERE id_usuario = ? AND id_usuario_objetivo = ? AND accion = 'CREAR_USUARIO'
                  AND valor_anterior IS NULL AND valor_nuevo = 'VENTANILLA'
                """, Integer.class, admin.getIdUsuario(), objetivo.getIdUsuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.evento_seguridad
                WHERE usuario = 'admin_hu034' AND tipo_evento = 'CAMBIO_ROL'
                  AND detalle LIKE '%operador_hu034%' AND ip_origen = '127.0.0.1'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void cambiaRolYRegistraAnteriorNuevoActorYObjetivo() throws Exception {
        Usuario objetivo = usuarios.save(new Usuario("analista_hu035", "Analista", "analista-hu035@example.org",
                claves.hashear("clave"), rol(NombreRol.ANALISTA_RENTAS), LocalDateTime.now()));
        mvc.perform(put("/api/admin/usuarios/{id}/rol", objetivo.getIdUsuario()).session(sesionAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"nuevoRol\":\"RESPONSABLE_DATOS\",\"motivo\":\"Traslado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("RESPONSABLE_DATOS"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.registro_auditoria
                WHERE id_usuario = ? AND id_usuario_objetivo = ? AND accion = 'CAMBIO_ROL'
                  AND valor_anterior = 'ANALISTA_RENTAS' AND valor_nuevo = 'RESPONSABLE_DATOS'
                  AND fecha_hora IS NOT NULL
                """, Integer.class, admin.getIdUsuario(), objetivo.getIdUsuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.usuario_rol WHERE id_usuario = ?",
                Integer.class, objetivo.getIdUsuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.evento_seguridad
                WHERE usuario = 'admin_hu034' AND tipo_evento = 'CAMBIO_ROL'
                  AND detalle LIKE '%analista_hu035%' AND detalle LIKE '%Traslado%'
                """, Integer.class)).isEqualTo(1);
        mvc.perform(put("/api/admin/usuarios/{id}/rol", objetivo.getIdUsuario()).session(sesionAdmin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoRol\":\"RESPONSABLE_DATOS\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ROL_IDENTICO"));
    }

    @Test
    void validaUsuarioRolYDuplicados() throws Exception {
        mvc.perform(put("/api/admin/usuarios/999999/rol").session(sesionAdmin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoRol\":\"GERENCIA\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/usuarios").session(sesionAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombreUsuario":"otro_hu034","nombreCompleto":"Otro",
                         "correo":"otro-hu034@example.org","claveInicial":"clave",
                         "rol":"NO_EXISTE"}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/usuarios").session(sesionAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombreUsuario":"admin_hu034","nombreCompleto":"Otro",
                         "correo":"otro-hu034@example.org","claveInicial":"clave",
                         "rol":"GERENCIA"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DUPLICADO"));
    }

    @Test
    void rechazaSesionAusenteYUsuarioSinRolAdmin() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("X-Usuario", "admin_hu034"))
                .andExpect(status().isForbidden());
        Usuario operador = usuarios.save(new Usuario("ventanilla_hu034", "Ventanilla",
                "ventanilla-hu034@example.org", claves.hashear("clave"), rol(NombreRol.VENTANILLA),
                LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, operador.getIdUsuario());
        mvc.perform(get("/api/admin/roles").session(sesion)).andExpect(status().isForbidden());
    }

    @Test
    void loginInternoHabilitaSesionAdministrador() throws Exception {
        var resultado = mvc.perform(post("/api/auth/interno/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"admin_hu034\",\"clave\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("admin_hu034"))
                .andExpect(jsonPath("$.roles[0]").value("ADMINISTRADOR"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
        mvc.perform(get("/api/admin/roles").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/interno/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("admin_hu034"));
    }

    @Test
    void meSinSesionInternaRetorna401() throws Exception {
        mvc.perform(get("/api/auth/interno/me")).andExpect(status().isUnauthorized());
    }

    private Rol rol(NombreRol nombre) {
        return roles.findByNombre(nombre).orElseThrow();
    }

    private MockHttpSession sesionAdmin() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, admin.getIdUsuario());
        return sesion;
    }
}
