package pe.municipalidad.tributario.ia;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU031: la jefatura aprueba la segmentación generada por el modelo y queda publicada como filtro del dashboard.
 * Los segmentos se insertan como los dejaría la generación (HU030), en borrador.
 */
@PruebaInterna
@Transactional
class SegmentoControllerTest {

    private static final String RUTA = "/api/segmentos";
    private static final String RUTA_APROBACIONES = RUTA + "/aprobaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void alAprobarLaSegmentacionQuedaPublicadaComoFiltro() throws Exception {
        long modelo = modelo("1.0");
        long puntual = segmento(modelo, "Puntual", "BORRADOR");
        long riesgo = segmento(modelo, "Alto riesgo", "BORRADOR");

        mockMvc.perform(get(RUTA).param("estado", "BORRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idSegmento == " + puntual + ")].modelo").value("kmeans-pagos"));

        aprobar("[" + puntual + ", " + riesgo + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicados", hasSize(2)))
                .andExpect(jsonPath("$.publicados[0].nombre").value("Alto riesgo"))
                .andExpect(jsonPath("$.publicados[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.publicados[0].aprobador").value("gerencia"))
                .andExpect(jsonPath("$.publicados[0].fechaAprobacion").value("2026-10-19T12:00:00"))
                .andExpect(jsonPath("$.idsRetirados", hasSize(0)));

        mockMvc.perform(get(RUTA + "/publicados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].nombre").value("Puntual"));
    }

    @Test
    void unaSegmentacionNuevaRetiraLaPublicadaAnterior() throws Exception {
        long anterior = segmento(modelo("1.0"), "Segmento anterior", "BORRADOR");
        aprobar("[" + anterior + "]").andExpect(status().isOk());
        long nuevo = segmento(modelo("2.0"), "Segmento nuevo", "BORRADOR");

        aprobar("[" + nuevo + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idsRetirados[0]").value(anterior));
        mockMvc.perform(get(RUTA + "/publicados"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idSegmento").value(nuevo));
        mockMvc.perform(get(RUTA).param("estado", "RETIRADO"))
                .andExpect(jsonPath("$[0].idSegmento").value(anterior));
    }

    @Test
    void seApruebaLaSegmentacionCompletaYDeUnSoloModelo() throws Exception {
        long modelo = modelo("1.0");
        long primero = segmento(modelo, "Puntual", "BORRADOR");
        long segundo = segmento(modelo, "Moroso", "BORRADOR");
        long deOtroModelo = segmento(modelo("2.0"), "Otro", "BORRADOR");

        aprobar("[" + primero + "]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTACION_INCOMPLETA"));
        aprobar("[" + primero + ", " + segundo + ", " + deOtroModelo + "]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTOS_DE_DISTINTO_MODELO"));
        aprobar("[" + primero + ", " + primero + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SEGMENTOS_REPETIDOS"));
        aprobar("[999999]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEGMENTO_NO_ENCONTRADO"));
        aprobar("[]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unSegmentoYaRevisadoNoSeApruebaDeNuevo() throws Exception {
        long publicado = segmento(modelo("1.0"), "Publicado", "BORRADOR");
        aprobar("[" + publicado + "]").andExpect(status().isOk());

        aprobar("[" + publicado + "]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTO_NO_BORRADOR"));
    }

    @Test
    void soloLaJefaturaAprueba() throws Exception {
        long segmento = segmento(modelo("1.0"), "Puntual", "BORRADOR");

        mockMvc.perform(post(RUTA_APROBACIONES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsSegmento\": [" + segmento + "]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA).with(user("analista")
                        .authorities(new SimpleGrantedAuthority("DASHBOARD:CONSULTAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA + "/publicados").with(user("analista")
                        .authorities(new SimpleGrantedAuthority("DASHBOARD:CONSULTAR"))))
                .andExpect(status().isOk());
    }

    private ResultActions aprobar(String ids) throws Exception {
        return mockMvc.perform(post(RUTA_APROBACIONES).with(csrf())
                .with(user("gerencia").authorities(new SimpleGrantedAuthority("SEGMENTOS:APROBAR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idsSegmento\": " + ids + "}"));
    }

    private long modelo(String version) {
        return jdbc.queryForObject("""
                INSERT INTO analitica.modelo_ia (nombre, tipo, version, hash_integridad, formato, fecha_registro)
                VALUES ('kmeans-pagos', 'SEGMENTACION', ?, repeat('a', 64), 'ONNX', TIMESTAMP '2026-10-01 08:00')
                RETURNING id_modelo
                """, Long.class, version);
    }

    private long segmento(long idModelo, String nombre, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO analitica.segmento (id_modelo, nombre, descripcion, tamano, variables_caracteristicas, estado)
                VALUES (?, ?, 'Segmento de prueba', 10, '{"porcentajePagoATiempo": 95}', ?)
                RETURNING id_segmento
                """, Long.class, idModelo, nombre, estado);
    }
}
