package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU019: buzón del contribuyente. Los contribuyentes son los de los datos de prueba (014502, DNI 41028765, y
 * 021390, DNI/RUC distinto). El reloj de los tests está fijo en 2026-10-19 12:00. Cada test se revierte al terminar.
 */
@PruebaIntegracion
@Transactional
class BuzonControllerTest {

    private static final String RUTA = "/api/buzon";
    private static final String CONTRIBUYENTE = "014502";
    private static final String DOCUMENTO_CONTRIBUYENTE = "41028765";
    private static final String OTRO_CONTRIBUYENTE = "021390";
    private static final String DOCUMENTO_OTRO_CONTRIBUYENTE = "20456781239";
    private static final LocalDateTime ENVIO_ANTIGUO = LocalDateTime.of(2026, 10, 10, 9, 0);
    private static final LocalDateTime ENVIO_RECIENTE = LocalDateTime.of(2026, 10, 15, 16, 30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentoBuzonRepository buzon;

    @Test
    void sinSesionNoSeAccedeAlBuzon() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
        mockMvc.perform(get(RUTA + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
    }

    @Test
    void listaSoloLosDocumentosDelContribuyenteDelMasRecienteAlMasAntiguo() throws Exception {
        guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-09", ENVIO_ANTIGUO);
        guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);
        guardar(DOCUMENTO_OTRO_CONTRIBUYENTE, "Estado de cuenta ajeno", ENVIO_RECIENTE);

        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.sinLeer").value(2))
                .andExpect(jsonPath("$.documentos[0].titulo").value("Estado de cuenta 2026-10"))
                .andExpect(jsonPath("$.documentos[0].tipo").value("ESTADO_CUENTA"))
                .andExpect(jsonPath("$.documentos[0].fechaEnvio").value("2026-10-15T16:30:00"))
                .andExpect(jsonPath("$.documentos[0].leido").value(false))
                .andExpect(jsonPath("$.documentos[1].titulo").value("Estado de cuenta 2026-09"));
    }

    @Test
    void unBuzonVacioDevuelveListaVacia() throws Exception {
        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.sinLeer").value(0))
                .andExpect(jsonPath("$.documentos").isEmpty());
    }

    @Test
    void listarNoRegistraLaLectura() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);

        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE))).andExpect(status().isOk());

        assertThat(buzon.findById(documento.getIdDocumento()).orElseThrow().getFechaPrimeraLectura()).isNull();
    }

    @Test
    void alAbrirPorPrimeraVezSeRegistraLaFechaYHoraDeLectura() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);

        mockMvc.perform(get(RUTA + "/" + documento.getIdDocumento()).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDocumento").value(documento.getIdDocumento()))
                .andExpect(jsonPath("$.titulo").value("Estado de cuenta 2026-10"))
                .andExpect(jsonPath("$.fechaEnvio").value("2026-10-15T16:30:00"))
                .andExpect(jsonPath("$.fechaPrimeraLectura").value("2026-10-19T12:00:00"))
                .andExpect(jsonPath("$.leido").value(true));

        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.sinLeer").value(0))
                .andExpect(jsonPath("$.documentos[0].fechaPrimeraLectura").value("2026-10-19T12:00:00"));
    }

    @Test
    void lasAperturasPosterioresNoCambianLaPrimeraLectura() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);
        LocalDateTime primeraLectura = LocalDateTime.of(2026, 10, 17, 8, 15);
        documento.marcarLeido(primeraLectura);
        buzon.saveAndFlush(documento);

        mockMvc.perform(get(RUTA + "/" + documento.getIdDocumento()).session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaPrimeraLectura").value("2026-10-17T08:15:00"));
    }

    @Test
    void permiteVolverAMarcarUnMensajeComoNoLeido() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);
        documento.marcarLeido(LocalDateTime.of(2026, 10, 17, 8, 15));
        buzon.saveAndFlush(documento);

        mockMvc.perform(post(RUTA + "/" + documento.getIdDocumento() + "/no-leido")
                        .session(sesion(CONTRIBUYENTE)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leido").value(false))
                .andExpect(jsonPath("$.fechaPrimeraLectura").value("2026-10-17T08:15:00"));

        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.sinLeer").value(1));
    }

    @Test
    void destacaArchivaYRestauraSinEliminarElDocumento() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);

        mockMvc.perform(patch(RUTA + "/" + documento.getIdDocumento() + "/destacado")
                        .session(sesion(CONTRIBUYENTE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.destacado").value(true));
        mockMvc.perform(get(RUTA + "?vista=DESTACADOS").session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(patch(RUTA + "/" + documento.getIdDocumento() + "/archivado")
                        .session(sesion(CONTRIBUYENTE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivado").value(true))
                .andExpect(jsonPath("$.fechaArchivado").value("2026-10-19T12:00:00"));
        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(RUTA + "?vista=ARCHIVADOS").session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(patch(RUTA + "/" + documento.getIdDocumento() + "/archivado")
                        .session(sesion(CONTRIBUYENTE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivado").value(false))
                .andExpect(jsonPath("$.fechaArchivado").value(nullValue()));
        assertThat(buzon.findById(documento.getIdDocumento())).isPresent();
    }

    @Test
    void buscaFiltraPaginaYSoloCuentaNoLeidosDeRecibidos() throws Exception {
        DocumentoBuzon reciente = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta octubre", ENVIO_RECIENTE);
        DocumentoBuzon antiguo = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta septiembre", ENVIO_ANTIGUO);
        antiguo.marcarLeido(LocalDateTime.of(2026, 10, 11, 8, 0));
        buzon.saveAndFlush(antiguo);
        guardar(DOCUMENTO_OTRO_CONTRIBUYENTE, "Estado de cuenta octubre ajeno", ENVIO_RECIENTE);

        mockMvc.perform(get(RUTA + "?busqueda=octubre&leido=false&pagina=0&tamanio=1")
                        .session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.sinLeer").value(1))
                .andExpect(jsonPath("$.documentos[0].idDocumento").value(reciente.getIdDocumento()))
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanio").value(1));
    }

    @Test
    void otroContribuyenteNoPuedeAbrirElDocumentoNiSeMarcaComoLeido() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta 2026-10", ENVIO_RECIENTE);

        mockMvc.perform(get(RUTA + "/" + documento.getIdDocumento()).session(sesion(OTRO_CONTRIBUYENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_NO_ENCONTRADO"));

        mockMvc.perform(get(RUTA).session(sesion(CONTRIBUYENTE)))
                .andExpect(jsonPath("$.documentos[0].fechaPrimeraLectura").value(nullValue()));
    }

    @Test
    void unDocumentoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(RUTA + "/999999").session(sesion(CONTRIBUYENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_NO_ENCONTRADO"));
    }

    private DocumentoBuzon guardar(String documentoContribuyente, String titulo, LocalDateTime fechaEnvio) {
        return buzon.saveAndFlush(DocumentoBuzon.deEstadoCuenta(documentoContribuyente, titulo, null, fechaEnvio));
    }

    @Test
    void descargaExigeSesionYPropiedadYNoInventaUnArchivo() throws Exception {
        DocumentoBuzon documento = guardar(DOCUMENTO_CONTRIBUYENTE, "Estado de cuenta", ENVIO_RECIENTE);
        String ruta = RUTA + "/" + documento.getIdDocumento() + "/descarga";
        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ruta).session(sesion(OTRO_CONTRIBUYENTE))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_NO_ENCONTRADO"));
        mockMvc.perform(get(ruta).session(sesion(CONTRIBUYENTE))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_DISPONIBLE"));
        assertThat(buzon.findById(documento.getIdDocumento()).orElseThrow().getFechaPrimeraLectura()).isNull();
    }

    private static MockHttpSession sesion(String codigoContribuyente) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, codigoContribuyente);
        return sesion;
    }
}
