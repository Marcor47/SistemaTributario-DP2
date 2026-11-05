package pe.municipalidad.tributario.arbitrio;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/* HU005: arbitrios de un predio con monto y estado de pago, para el Analista de Rentas. Fecha de corte 2026-10-19. */
@PruebaIntegracion
@Transactional
class ArbitrioPredioControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;

    @MockitoSpyBean
    private SamnetGateway samnet;

    /*
     * 021390-01: comercio de 420 m² en la zona Z2. Arbitrios 2025 por mes: barrido 16,20 + recolección 41,50 x 2,10
     * (área / 200 m²) = 87,15 + parques 10,80 + serenazgo 34,20 = 148,35; en 2026 se reajustan 3 %: 152,82.
     * Pagó hasta julio de 2025; de agosto de 2025 a setiembre de 2026 están vencidas y octubre a diciembre pendientes.
     */
    @Test
    void devuelveLasCuotasMensualesConSusServiciosEstadoEInteres() throws Exception {
        mvc.perform(get("/api/predios/021390-01/arbitrios").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.codigoPredio").value("021390-01"))
                .andExpect(jsonPath("$.predio.ubicacion").value("Jr. Los Precursores 220, San Miguel"))
                .andExpect(jsonPath("$.predio.titulares", hasSize(1)))
                .andExpect(jsonPath("$.predio.titulares[0].codigoContribuyente").value("021390"))
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.cuotas", hasSize(24)))
                .andExpect(jsonPath("$.cuotas[0].periodo").value("2025-01"))
                .andExpect(jsonPath("$.cuotas[0].fechaVencimiento").value("2025-02-28"))
                .andExpect(jsonPath("$.cuotas[0].emitido").value(148.35))
                .andExpect(jsonPath("$.cuotas[0].estado").value("PAGADA"))
                .andExpect(jsonPath("$.cuotas[0].servicios", hasSize(4)))
                .andExpect(jsonPath("$.cuotas[0].servicios[?(@.servicio == 'RECOLECCION_RESIDUOS')].monto")
                        .value(87.15))
                .andExpect(jsonPath("$.cuotas[7].periodo").value("2025-08"))
                .andExpect(jsonPath("$.cuotas[7].estado").value("VENCIDA"))
                .andExpect(jsonPath("$.cuotas[7].diasVencida").value(416))
                .andExpect(jsonPath("$.cuotas[7].interesMoratorio").value(18.51))
                .andExpect(jsonPath("$.cuotas[12].emitido").value(152.82))
                .andExpect(jsonPath("$.resumen.cuotasPagadas").value(7))
                .andExpect(jsonPath("$.resumen.cuotasVencidas").value(14))
                .andExpect(jsonPath("$.resumen.cuotasPendientes").value(3))
                .andExpect(jsonPath("$.resumen.totalSaldo").value(2575.59))
                .andExpect(jsonPath("$.resumen.porServicio", hasSize(4)))
                .andExpect(jsonPath("$.resumen.porServicio[0].servicio").value("BARRIDO_CALLES"))
                .andExpect(jsonPath("$.resumen.porServicio[0].emitido").value(394.68));
    }

    @Test
    void soloIncluyeArbitriosDelPredioPedido() throws Exception {
        mvc.perform(get("/api/predios/014502-01/arbitrios").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotas", hasSize(24)))
                .andExpect(jsonPath("$.cuotas[*].codigoContribuyente").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("014502"))))
                .andExpect(jsonPath("$.resumen.cuotasPagadas").value(24))
                .andExpect(jsonPath("$.resumen.totalSaldo").value(0));
    }

    /* En copropiedad al 50 % cada titular tiene su mitad de cada cuota; 058934 dejó de pagar en 2026. */
    @Test
    void conCopropiedadCadaTitularTieneSuParteDeLaCuota() throws Exception {
        mvc.perform(get("/api/predios/058934-01/arbitrios").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predio.titulares", hasSize(2)))
                .andExpect(jsonPath("$.cuotas", hasSize(48)))
                .andExpect(jsonPath("$.cuotas[0].emitido").value(21.60))
                .andExpect(jsonPath("$.cuotas[1].emitido").value(21.60))
                .andExpect(jsonPath("$.resumen.cuotasPagadas").value(27))
                .andExpect(jsonPath("$.resumen.cuotasVencidas").value(15))
                .andExpect(jsonPath("$.resumen.cuotasPendientes").value(6));
    }

    @Test
    void elAdministradorNoAccedeAModulosTributarios() throws Exception {
        mvc.perform(get("/api/predios/033218-01/arbitrios").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void predioInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/predios/999999-99/arbitrios").session(sesion(NombreRol.ANALISTA_RENTAS)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PREDIO_NO_ENCONTRADO"));
    }

    @Test
    void sinSesionInternaDevuelve401() throws Exception {
        mvc.perform(get("/api/predios/021390-01/arbitrios")).andExpect(status().isUnauthorized());
    }

    @Test
    void conUnRolSinPermisoDevuelve403() throws Exception {
        mvc.perform(get("/api/predios/021390-01/arbitrios").session(sesion(NombreRol.VENTANILLA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unContribuyenteNoPuedeConsultarArbitriosDeUnPredio() throws Exception {
        MockHttpSession contribuyente = new MockHttpSession();
        contribuyente.setAttribute("citizenCode", "021390");
        mvc.perform(get("/api/predios/021390-01/arbitrios").session(contribuyente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void siSamnetNoRespondeInformaQueNoEstaDisponible() throws Exception {
        doThrow(new SamnetNoDisponibleException(null)).when(samnet).buscarPredio(anyString(), org.mockito.ArgumentMatchers.any());

        mvc.perform(get("/api/predios/021390-01/arbitrios").session(sesion(NombreRol.ANALISTA_RENTAS)))
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
