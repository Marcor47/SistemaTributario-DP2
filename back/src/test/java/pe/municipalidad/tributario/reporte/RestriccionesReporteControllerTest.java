package pe.municipalidad.tributario.reporte;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.*;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class RestriccionesReporteControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired ClaveInternaService claves;
    @Autowired JdbcTemplate jdbc;

    @Test
    void soloElAdministradorPuedeCrearConsultarYEliminarUnaRegla() throws Exception {
        MockHttpSession admin = sesion(NombreRol.ADMINISTRADOR);
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);
        String ruta = "/api/admin/restricciones-reportes";
        String peticion = "{\"rol\":\"GERENCIA\",\"tipoReporte\":\"MOROSOS\",\"dimension\":\"ZONA\",\"valor\":\" maranga \"}";
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).session(gerencia)).andExpect(status().isForbidden());
        mvc.perform(post(ruta).session(admin).contentType(MediaType.APPLICATION_JSON).content(peticion))
                .andExpect(status().isForbidden());
        String respuesta = mvc.perform(post(ruta).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(peticion)).andExpect(status().isCreated()).andExpect(jsonPath("$.permitido").value(false))
                .andExpect(jsonPath("$.valor").value("Maranga"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(respuesta, "$.id");
        mvc.perform(get(ruta).session(admin)).andExpect(status().isOk());
        mvc.perform(delete(ruta + "/" + id).session(admin).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void rechazaZonasInexistentesEnVezDeGuardarUnaReglaIneficaz() throws Exception {
        mvc.perform(post("/api/admin/restricciones-reportes").session(sesion(NombreRol.ADMINISTRADOR)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"GERENCIA\",\"tipoReporte\":\"MOROSOS\",\"dimension\":\"ZONA\",\"valor\":\"Marangaa\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ZONA_NO_ENCONTRADA"));
    }

    @Test
    void publicaLasOpcionesDeSegmentoYLaDimensionEnLaConfiguracion() throws Exception {
        mvc.perform(get("/api/admin/restricciones-reportes/opciones").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentos").isArray())
                .andExpect(jsonPath("$.dimensiones", org.hamcrest.Matchers.hasItem("SEGMENTO")))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder(
                        "GERENCIA", "ANALISTA_RENTAS")));
    }

    @Test
    void reemplazaLaConfiguracionCompletaDeLosRolesEnUnaSolaOperacion() throws Exception {
        MockHttpSession admin = sesion(NombreRol.ADMINISTRADOR);
        String cuerpo = """
                {"roles":{"GERENCIA":[
                    {"tipoReporte":"MOROSOS","dimension":"REPORTE","valor":null},
                    {"tipoReporte":"PRICOS","dimension":"ZONA","valor":"Pando"}
                ],"ANALISTA_RENTAS":[]}}
                """;
        mvc.perform(put("/api/admin/restricciones-reportes").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rol").value("GERENCIA"))
                .andExpect(jsonPath("$[0].permitido").value(false));
        mvc.perform(get("/api/admin/restricciones-reportes").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        Integer auditorias = jdbc.queryForObject("""
                SELECT count(*) FROM seguridad.registro_auditoria
                WHERE accion = 'CAMBIAR_RESTRICCION_REPORTE' AND entidad = 'RestriccionReporte'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(auditorias).isPositive();
    }

    @Test
    void unRolSinElPermisoDeConfiguracionNoPuedeReemplazarReglas() throws Exception {
        mvc.perform(put("/api/admin/restricciones-reportes").session(sesion(NombreRol.GERENCIA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":{\"GERENCIA\":[]}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaUnSegmentoQueNoEstaEnLaUltimaSegmentacionPublicada() throws Exception {
        MockHttpSession admin = sesion(NombreRol.ADMINISTRADOR);
        mvc.perform(post("/api/admin/restricciones-reportes").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"GERENCIA\",\"tipoReporte\":\"MOROSOS\",\"dimension\":\"SEGMENTO\",\"valor\":\"RIESGO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SEGMENTO_NO_ENCONTRADO"));
    }

    private MockHttpSession sesion(NombreRol nombre) {
        var rol = roles.findByNombre(nombre).orElseThrow();
        var usuario = usuarios.save(new Usuario("hu045_" + nombre.name().toLowerCase(), "Prueba HU045",
                "hu045_" + nombre.name().toLowerCase() + "@example.org", claves.hashear("PruebaSM#2026"),
                rol, LocalDateTime.now()));
        var sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
