package pe.municipalidad.tributario.predios;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU036 (RF-048): registro de un predio nuevo con su contribuyente en SAMNET sintético. El contribuyente 014502 es
 * activo y el 072405 está inactivo; el predio 014502-01 ya existe.
 */
@PruebaIntegracion
@Transactional
class PredioRegistroControllerTest {

    private static final String RUTA = "/api/predios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registraUnPredioNuevoConSuContribuyenteYQuedaConsultable() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-90", "014502", "")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/predios?consulta=014502-90"))
                .andExpect(jsonPath("$.codigoPredio").value("014502-90"))
                .andExpect(jsonPath("$.tipoPredio").value("URBANO"))
                .andExpect(jsonPath("$.areaTerreno").value(120.5))
                .andExpect(jsonPath("$.titulares", hasSize(1)))
                .andExpect(jsonPath("$.titulares[0].codigo").value("014502"))
                .andExpect(jsonPath("$.titulares[0].nombre").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.titulares[0].condicionPropiedad").value("PROPIETARIO"))
                .andExpect(jsonPath("$.titulares[0].porcentajePropiedad").value(100));

        mockMvc.perform(get(RUTA).param("consulta", "014502-90").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].ubicacion").value("Av. Libertad 500, San Miguel"))
                .andExpect(jsonPath("$.contenido[0].titulares[0].codigo").value("014502"));
    }

    @Test
    void respetaLaCondicionYElPorcentajeIndicados() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-91", "014502",
                                ", \"condicionPropiedad\": \"copropietario\", \"porcentajePropiedad\": 50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulares[0].condicionPropiedad").value("COPROPIETARIO"))
                .andExpect(jsonPath("$.titulares[0].porcentajePropiedad").value(50));
    }

    @Test
    void rechazaUnPredioQueYaExisteSinModificarlo() throws Exception {
        String ubicacionOriginal = jdbc.queryForObject(
                "SELECT ubicacion FROM samnet.predio WHERE codigo_predio = '014502-01'", String.class);

        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-01", "014502", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PREDIO_YA_REGISTRADO"));

        String ubicacionActual = jdbc.queryForObject(
                "SELECT ubicacion FROM samnet.predio WHERE codigo_predio = '014502-01'", String.class);
        org.assertj.core.api.Assertions.assertThat(ubicacionActual).isEqualTo(ubicacionOriginal);
    }

    @Test
    void elRegistroDuplicadoNoCreaOtraTitularidad() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-92", "014502", "")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-92", "058934", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PREDIO_YA_REGISTRADO"));

        Integer titulares = jdbc.queryForObject(
                "SELECT count(*) FROM samnet.predio_titular WHERE codigo_predio = '014502-92'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(titulares).isEqualTo(1);
    }

    @Test
    void rechazaUnContribuyenteInexistenteOInactivo() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("999999-01", "999999", "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_NO_ENCONTRADO"));
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("072405-90", "072405", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRIBUYENTE_INACTIVO"));
    }

    @Test
    void validaLosDatosObligatoriosYLosRangos() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles", hasItem("codigoContribuyente: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("codigoPredio: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("ubicacion: es obligatoria")))
                .andExpect(jsonPath("$.detalles", hasItem("tipoPredio: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("areaTerreno: es obligatoria")))
                .andExpect(jsonPath("$.detalles", hasItem("zona: es obligatoria")));

        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-93", "014502", "").replace("120.50", "-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("areaTerreno: debe ser mayor que 0")));
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("codigo/invalido", "014502", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem(
                        "codigoPredio: admite letras, números y guion, hasta 12 caracteres")));
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-94", "014502", "")
                                .replace("\"URBANO\"", "\"INEXISTENTE\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mockMvc.perform(post(RUTA).with(csrf()).with(registrador()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-95", "014502", "").replace("2026-09-01", "2999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("fechaAdquisicion: no puede ser una fecha futura")));
    }

    @Test
    void exigeSesionYElPermisoDeRegistro() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("014502-96", "014502", "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(RUTA).with(csrf()).with(consultor()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("014502-96", "014502", "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        Integer creados = jdbc.queryForObject(
                "SELECT count(*) FROM samnet.predio WHERE codigo_predio = '014502-96'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(creados).isZero();
    }

    private static String cuerpo(String codigoPredio, String codigoContribuyente, String extra) {
        return """
                {"codigoContribuyente": "%s", "codigoPredio": "%s", "numeroCatastral": "150136-010-009",
                 "ubicacion": "Av. Libertad 500, San Miguel", "usoPredio": "Vivienda", "tipoPredio": "URBANO",
                 "areaTerreno": 120.50, "baseImponible": 150000.00, "fechaAdquisicion": "2026-09-01",
                 "zona": "Costanera"%s}
                """.formatted(codigoContribuyente, codigoPredio, extra);
    }

    private static RequestPostProcessor registrador() {
        return user("analista").authorities(new SimpleGrantedAuthority("PREDIOS:REGISTRAR"),
                new SimpleGrantedAuthority("PREDIOS:CONSULTAR"));
    }

    private static RequestPostProcessor consultor() {
        return user("consultor").authorities(new SimpleGrantedAuthority("PREDIOS:CONSULTAR"));
    }
}
