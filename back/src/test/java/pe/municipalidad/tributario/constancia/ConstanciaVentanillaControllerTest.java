package pe.municipalidad.tributario.constancia;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/*
 * Pruebas unitarias de la capa web/REST para ConstanciaVentanillaController (HU042 por pasos, RF-055).
 * Valida peticiones HTTP, serialización y códigos de estado de cada paso del flujo presencial.
 */
@ExtendWith(MockitoExtension.class)
class ConstanciaVentanillaControllerTest {

    private static final String RUTA_VENTANILLA = "/api/ventanilla/constancias";
    private static final String DNI = "41028765";
    private static final String TRABAJADOR = "ventanilla";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 10, 30, 0);

    @Mock
    private ConstanciaVentanillaService constanciaVentanillaService;

    @InjectMocks
    private ConstanciaVentanillaController controller;

    private MockMvc mockMvc;
    private Principal trabajador;
    private SolicitudVentanillaRespuesta solicitud;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        trabajador = () -> TRABAJADOR;
        solicitud = new SolicitudVentanillaRespuesta(
                15L, CanalSolicitud.VENTANILLA, EstadoConstancia.INICIADA, AHORA, DNI, "DNI", "014502",
                "GARCÍA TORRES, MARÍA ELENA", null, null, null, null, null);
    }

    @Test
    void consultarRetornaDatosContribuyentePrediosYDeuda() throws Exception {
        ConsultaContribuyenteVentanillaRespuesta.DatosContribuyenteDto datosContribuyente =
                new ConsultaContribuyenteVentanillaRespuesta.DatosContribuyenteDto(
                        "014502", "DNI", DNI, "GARCÍA TORRES, MARÍA ELENA");
        PredioResumenVentanilla predio = new PredioResumenVentanilla(
                "PRD-014502-01", "CATASTRO-100", "Av. La Marina 1500", "URBANO", "ZONA 1");
        DeudaConstanciaRespuesta deuda = new DeudaConstanciaRespuesta(java.math.BigDecimal.ZERO, List.of());

        when(constanciaVentanillaService.consultar(DNI))
                .thenReturn(new ConsultaContribuyenteVentanillaRespuesta(datosContribuyente, List.of(predio), deuda));

        mockMvc.perform(get(RUTA_VENTANILLA + "/consulta")
                        .param("documento", DNI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.numeroDocumento", is(DNI)))
                .andExpect(jsonPath("$.predios", hasSize(1)))
                .andExpect(jsonPath("$.deuda.puedeSolicitar", is(true)));

        verify(constanciaVentanillaService).consultar(DNI);
    }

    @Test
    void crearSolicitudRetorna201ConLaSolicitudEnCanalVentanilla() throws Exception {
        when(constanciaVentanillaService.crearSolicitud(eq(DNI), eq(TRABAJADOR))).thenReturn(solicitud);

        mockMvc.perform(post(RUTA_VENTANILLA + "/solicitudes")
                        .principal(trabajador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentoContribuyente\": \"%s\"}".formatted(DNI)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSolicitud", is(15)))
                .andExpect(jsonPath("$.canal", is("VENTANILLA")))
                .andExpect(jsonPath("$.estado", is("INICIADA")))
                .andExpect(jsonPath("$.nombreContribuyente", is("GARCÍA TORRES, MARÍA ELENA")));

        verify(constanciaVentanillaService).crearSolicitud(eq(DNI), eq(TRABAJADOR));
    }

    @Test
    void obtenerDocumentoFirmaRetornaElDocumentoParaElDispositivo() throws Exception {
        DocumentoFirmaRespuesta documento = new DocumentoFirmaRespuesta(
                15L, "solicitud-constancia-15.txt", "text/plain; charset=UTF-8",
                java.util.Base64.getEncoder().encodeToString("SOLICITUD".getBytes()), "hash-sha256", true,
                new DocumentoFirmaRespuesta.DatosContribuyente("014502", "GARCÍA TORRES, MARÍA ELENA", "DNI", DNI));
        when(constanciaVentanillaService.obtenerDocumentoFirma(15L)).thenReturn(documento);

        mockMvc.perform(get(RUTA_VENTANILLA + "/15/documento-firma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud", is(15)))
                .andExpect(jsonPath("$.documentoDePrueba", is(true)))
                .andExpect(jsonPath("$.hashSha256", is("hash-sha256")));

        verify(constanciaVentanillaService).obtenerDocumentoFirma(15L);
    }

    @Test
    void registrarFirmaRetornaLaSolicitudFirmada() throws Exception {
        SolicitudVentanillaRespuesta firmada = new SolicitudVentanillaRespuesta(
                15L, CanalSolicitud.VENTANILLA, EstadoConstancia.FIRMADA, AHORA, DNI, "DNI", "014502",
                "GARCÍA TORRES, MARÍA ELENA", null,
                new SolicitudConstanciaRespuesta.FirmaRespuesta("ENTIDAD DE PRUEBA", AHORA, "hash-documento"),
                null, null, null);
        when(constanciaVentanillaService.registrarFirma(eq(15L), any(FirmaSolicitudConstancia.class), eq(TRABAJADOR)))
                .thenReturn(firmada);

        mockMvc.perform(post(RUTA_VENTANILLA + "/15/firma")
                        .principal(trabajador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmaBase64\": \"ZklJTUE=\", \"certificado\": \"CERTIFICADO DE PRUEBA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("FIRMADA")))
                .andExpect(jsonPath("$.firmaContribuyente.entidadCertificadora", is("ENTIDAD DE PRUEBA")));

        verify(constanciaVentanillaService).registrarFirma(eq(15L), any(FirmaSolicitudConstancia.class), eq(TRABAJADOR));
    }

    @Test
    void verificarDeudaRetornaLaSolicitudConOrdenYDeuda() throws Exception {
        SolicitudVentanillaRespuesta verificada = new SolicitudVentanillaRespuesta(
                15L, CanalSolicitud.VENTANILLA, EstadoConstancia.ORDEN_PAGO_EMITIDA, AHORA, DNI, "DNI", "014502",
                "GARCÍA TORRES, MARÍA ELENA", null, null,
                new OrdenPagoConstanciaRespuesta(7L, BigDecimal.valueOf(10.00), EstadoTransaccion.INICIADA, AHORA,
                        null, null, null),
                new DeudaConstanciaRespuesta(BigDecimal.ZERO, List.of()), null);
        when(constanciaVentanillaService.verificarDeuda(15L, TRABAJADOR)).thenReturn(verificada);

        mockMvc.perform(post(RUTA_VENTANILLA + "/15/verificacion").principal(trabajador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ORDEN_PAGO_EMITIDA")))
                .andExpect(jsonPath("$.ordenPago.monto", is(10.0)))
                .andExpect(jsonPath("$.deuda.puedeSolicitar", is(true)));

        verify(constanciaVentanillaService).verificarDeuda(15L, TRABAJADOR);
    }

    @Test
    void confirmarPagoRetornaLaSolicitudPagadaConComprobante() throws Exception {
        SolicitudVentanillaRespuesta pagada = new SolicitudVentanillaRespuesta(
                15L, CanalSolicitud.VENTANILLA, EstadoConstancia.PAGADA, AHORA, DNI, "DNI", "014502",
                "GARCÍA TORRES, MARÍA ELENA", null, null,
                new OrdenPagoConstanciaRespuesta(7L, BigDecimal.valueOf(10.00), EstadoTransaccion.CONFIRMADA, AHORA,
                        "CONST-VENT-7", null, null),
                null, null);
        when(constanciaVentanillaService.confirmarPago(15L, TRABAJADOR)).thenReturn(pagada);

        mockMvc.perform(post(RUTA_VENTANILLA + "/15/pago").principal(trabajador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.ordenPago.comprobante", is("CONST-VENT-7")));

        verify(constanciaVentanillaService).confirmarPago(15L, TRABAJADOR);
    }

    @Test
    void emitirRetorna201ConLosDatosDeLaConstancia() throws Exception {
        ConstanciaVentanillaRespuesta respuesta = new ConstanciaVentanillaRespuesta(
                "CNA-2026-000001", 15L, DNI, "GARCÍA TORRES, MARÍA ELENA", "014502",
                AHORA, "Todos los predios del contribuyente", "Hasta la fecha de emisión", TRABAJADOR,
                "Ventanilla de Pruebas", "VENTANILLA", "EMITIDA", "CONST-VENT-15", BigDecimal.valueOf(10.00), true);

        when(constanciaVentanillaService.emitir(eq(15L), any(EmitirConstanciaVentanillaSolicitud.class),
                eq(TRABAJADOR))).thenReturn(respuesta);

        mockMvc.perform(post(RUTA_VENTANILLA + "/15/emitir")
                        .principal(trabajador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predios\": null, \"prediosTexto\": null, \"rangoFechas\": null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroConstancia", is("CNA-2026-000001")))
                .andExpect(jsonPath("$.canal", is("VENTANILLA")))
                .andExpect(jsonPath("$.estado", is("EMITIDA")))
                .andExpect(jsonPath("$.trabajadorEmisor", is(TRABAJADOR)))
                .andExpect(jsonPath("$.montoDerechoTramite", is(10.0)));

        verify(constanciaVentanillaService).emitir(eq(15L), any(EmitirConstanciaVentanillaSolicitud.class),
                eq(TRABAJADOR));
    }

    @Test
    void descargarPdfRetornaContentTypePdfYCabeceraContentDisposition() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 de prueba".getBytes();
        when(constanciaVentanillaService.obtenerPdf("CNA-2026-000001")).thenReturn(pdfBytes);

        mockMvc.perform(get(RUTA_VENTANILLA + "/CNA-2026-000001/descarga"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("CNA-2026-000001.pdf")))
                .andExpect(content().bytes(pdfBytes));

        verify(constanciaVentanillaService).obtenerPdf("CNA-2026-000001");
    }

    @Test
    void listarRetornaListaDeConstancias() throws Exception {
        ConstanciaVentanillaRespuesta item = new ConstanciaVentanillaRespuesta(
                "CNA-2026-000001", 101L, DNI, "GARCÍA TORRES, MARÍA ELENA", "014502",
                LocalDateTime.now(), "Todos", "Hasta emisión", TRABAJADOR, "Ventanilla de Pruebas",
                "VENTANILLA", "EMITIDA", "CONST-VENT-55", BigDecimal.valueOf(10.00), true);

        when(constanciaVentanillaService.listar(DNI)).thenReturn(List.of(item));

        mockMvc.perform(get(RUTA_VENTANILLA).param("documento", DNI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numeroConstancia", is("CNA-2026-000001")));

        verify(constanciaVentanillaService).listar(DNI);
    }
}
