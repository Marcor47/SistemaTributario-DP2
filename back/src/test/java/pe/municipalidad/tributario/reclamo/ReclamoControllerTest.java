package pe.municipalidad.tributario.reclamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class ReclamoControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired ClaveInternaService claves;
    @Autowired ReclamoRepository reclamos;
    @Autowired AreaMunicipalRepository areas;
    @Autowired DocumentoBuzonRepository documentosBuzon;

    private Usuario trabajador;
    private Usuario gestor;
    private Usuario responsable;

    @BeforeEach
    void preparar() {
        trabajador = usuarios.save(new Usuario("trabajador_hu050", "Trabajador HU050",
                "trabajador-hu050@example.org", claves.hashear("ClaveSegura#2026"),
                roles.findByNombre(NombreRol.ANALISTA_RENTAS).orElseThrow(), LocalDateTime.now()));
        gestor = usuarios.save(new Usuario("gestor_hu050", "Gestor HU050", "gestor-hu050@example.org",
                claves.hashear("ClaveSegura#2026"), roles.findByNombre(NombreRol.GESTOR_RECLAMOS).orElseThrow(),
                LocalDateTime.now()));
        responsable = usuarios.save(new Usuario("responsable_hu050", "Responsable HU050",
                "responsable-hu050@example.org", claves.hashear("ClaveSegura#2026"),
                roles.findByNombre(NombreRol.GERENCIA).orElseThrow(), LocalDateTime.now()));
    }

    @Test
    void trabajadorRegistraYConsultaSoloSusReclamos() throws Exception {
        mvc.perform(post("/api/reclamos").session(sesionInterna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("DOCUMENTO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroReclamo").value(org.hamcrest.Matchers.matchesPattern("REC-2026-\\d{6}")))
                .andExpect(jsonPath("$.tipoReclamante").value("TRABAJADOR"))
                .andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.derivaciones").isEmpty());

        mvc.perform(get("/api/reclamos/mios").session(sesionInterna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comentario").value("El documento no aparece en el sistema."))
                .andExpect(jsonPath("$[0].tipoReclamante").value("TRABAJADOR"));
        assertThat(reclamos.findAll()).hasSize(1);
    }

    @Test
    void contribuyenteRegistraConSuDocumentoSinPoderSuplantarlo() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, "021390");

        mvc.perform(post("/api/reclamos").session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("ATENCION")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoReclamante").value("CONTRIBUYENTE"));
        mvc.perform(get("/api/reclamos/mios").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoReclamo").value("ATENCION"));

        Reclamo guardado = reclamos.findAll().getFirst();
        assertThat(guardado.getDocumentoContribuyente()).isNotBlank();
        assertThat(guardado.getUsuarioReclamante()).isNull();
    }

    @Test
    void rechazaTipoAjenoFechaFuturaYDatosInvalidos() throws Exception {
        mvc.perform(post("/api/reclamos").session(sesionInterna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("ATENCION")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TIPO_RECLAMO_NO_PERMITIDO"));
        mvc.perform(post("/api/reclamos").session(sesionInterna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipoReclamo":"DOCUMENTO","fechaHoraIncidente":"2099-01-01T10:00:00",
                         "comentario":"Incidente futuro"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_INCIDENTE_FUTURA"));
        mvc.perform(post("/api/reclamos").session(sesionInterna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeSesionYCsrfSinCrearDatos() throws Exception {
        mvc.perform(get("/api/reclamos/mios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reclamos").session(sesionInterna())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("DOCUMENTO")))
                .andExpect(status().isForbidden());
        assertThat(reclamos.findAll()).isEmpty();
    }

    @Test
    void gestorDerivaYResponsableRespondeReclamoInterno() throws Exception {
        mvc.perform(post("/api/reclamos").session(sesionInterna(trabajador)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("DOCUMENTO")))
                .andExpect(status().isCreated());
        Reclamo reclamo = reclamos.findAll().getFirst();
        AreaMunicipal area = areas.findByActivaTrueOrderByNombreAsc().getFirst();

        mvc.perform(get("/api/reclamos/gestion").session(sesionInterna(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reclamo.estado").value("ENVIADO"));
        mvc.perform(get("/api/reclamos/catalogos").session(sesionInterna(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.areas").isNotEmpty())
                .andExpect(jsonPath("$.responsables[?(@.nombreUsuario == 'responsable_hu050')]").exists());

        mvc.perform(post("/api/reclamos/gestion/{id}/derivaciones", reclamo.getIdReclamo())
                .session(sesionInterna(gestor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idArea":%d,"idResponsable":%d,"observacion":"Revisar el documento fuente."}
                        """.formatted(area.getIdArea(), responsable.getIdUsuario())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reclamo.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.derivaciones").doesNotExist())
                .andExpect(jsonPath("$.reclamo.derivaciones[0].responsable").value("Responsable HU050"));

        mvc.perform(get("/api/reclamos/asignados").session(sesionInterna(responsable)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].numeroReclamo").doesNotExist())
                .andExpect(jsonPath("$[0].reclamo.numeroReclamo").value(reclamo.getNumeroReclamo()));
        mvc.perform(post("/api/reclamos/asignados/{id}/respuesta", reclamo.getIdReclamo())
                .session(sesionInterna(responsable)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Se corrigió el documento y se notificó al área solicitante.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reclamo.estado").value("APROBADO_CON_RESPUESTA"));

        mvc.perform(get("/api/reclamos/mios").session(sesionInterna(trabajador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].respuesta").value("Se corrigió el documento y se notificó al área solicitante."));
    }

    @Test
    void respuestaAContribuyenteGeneraDocumentoEnBuzon() throws Exception {
        MockHttpSession sesionCiudadano = new MockHttpSession();
        sesionCiudadano.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, "021390");
        mvc.perform(post("/api/reclamos").session(sesionCiudadano).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("ATENCION")))
                .andExpect(status().isCreated());
        Reclamo reclamo = reclamos.findAll().getFirst();
        AreaMunicipal area = areas.findByActivaTrueOrderByNombreAsc().getFirst();
        mvc.perform(post("/api/reclamos/gestion/{id}/derivaciones", reclamo.getIdReclamo())
                .session(sesionInterna(gestor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idArea\":" + area.getIdArea() + ",\"idResponsable\":"
                        + responsable.getIdUsuario() + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reclamos/asignados/{id}/respuesta", reclamo.getIdReclamo())
                .session(sesionInterna(responsable)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"La atención fue revisada y se aplicaron medidas correctivas.\"}"))
                .andExpect(status().isOk());
        assertThat(documentosBuzon.findAll()).singleElement()
                .satisfies(documento -> assertThat(documento.getReclamo().getIdReclamo())
                        .isEqualTo(reclamo.getIdReclamo()));
    }

    @Test
    void impideGestionSinRolRespuestaAjenaYPermiteCancelarAlPropietario() throws Exception {
        mvc.perform(post("/api/reclamos").session(sesionInterna(trabajador)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(solicitud("DOCUMENTO")))
                .andExpect(status().isCreated());
        Reclamo reclamo = reclamos.findAll().getFirst();
        mvc.perform(get("/api/reclamos/gestion").session(sesionInterna(trabajador)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reclamos/asignados/{id}/respuesta", reclamo.getIdReclamo())
                .session(sesionInterna(responsable)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Respuesta no autorizada.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reclamos/mios/{id}/cancelar", reclamo.getIdReclamo())
                .session(sesionInterna(trabajador)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    private MockHttpSession sesionInterna() {
        return sesionInterna(trabajador);
    }

    private MockHttpSession sesionInterna(Usuario usuario) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }

    private String solicitud(String tipo) {
        return """
                {"tipoReclamo":"%s","referencia":"MEM-2026-050",
                 "fechaHoraIncidente":"2026-10-18T09:30:00",
                 "comentario":"El documento no aparece en el sistema."}
                """.formatted(tipo);
    }
}
