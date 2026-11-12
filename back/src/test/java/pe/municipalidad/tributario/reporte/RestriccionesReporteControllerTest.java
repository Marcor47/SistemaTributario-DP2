package pe.municipalidad.tributario.reporte;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
    void noAceptaPrometerUnaRestriccionPorSegmentoQueAunNoSeAplica() throws Exception {
        MockHttpSession admin = sesion(NombreRol.ADMINISTRADOR);
        mvc.perform(post("/api/admin/restricciones-reportes").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"GERENCIA\",\"tipoReporte\":\"MOROSOS\",\"dimension\":\"SEGMENTO\",\"valor\":\"RIESGO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DIMENSION_NO_DISPONIBLE"));
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
