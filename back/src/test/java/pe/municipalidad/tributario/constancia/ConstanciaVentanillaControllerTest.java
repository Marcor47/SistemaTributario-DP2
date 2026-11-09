package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Pruebas unitarias de la capa web/REST para ConstanciaVentanillaController (HU042, RF-055).
 * Valida peticiones HTTP, serialización, cabeceras de descarga de PDF y códigos de estado.
 */
@ExtendWith(MockitoExtension.class)
class ConstanciaVentanillaControllerTest {

    private static final String RUTA_VENTANILLA = "/api/ventanilla/constancias";
    private static final String DNI = "41028765";
    private static final String TRABAJADOR = "ventanilla";

    @Mock
    private ConstanciaVentanillaService constanciaVentanillaService;

    @InjectMocks
    private ConstanciaVentanillaController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void consultarRetornaDatosContribuyentePrediosYDeuda() throws Exception {
        ConsultaContribuyenteVentanillaRespuesta.DatosContribuyenteDto datosContribuyente =
                new ConsultaContribuyenteVentanillaRespuesta.DatosContribuyenteDto(
                        "014502", "DNI", DNI, "GARCÍA TORRES, MARÍA ELENA");
        PredioResumenVentanilla predio = new PredioResumenVentanilla(
                "PRD-014502-01", "CATASTRO-100", "Av. La Marina 1500", "URBANO", "ZONA 1");
        DeudaConstanciaRespuesta deuda = new DeudaConstanciaRespuesta(BigDecimal.ZERO, List.of());

        when(constanciaVentanillaService.consultar(DNI))
                .thenReturn(new ConsultaContribuyenteVentanillaRespuesta(datosContribuyente, List.of(predio), deuda));

        mockMvc.perform(get(RUTA_VENTANILLA + "/consulta")
                        .param("documento", DNI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contribuyente.numeroDocumento", is(DNI)))
                .andExpect(jsonPath("$.contribuyente.nombreRazonSocial", is("GARCÍA TORRES, MARÍA ELENA")))
                .andExpect(jsonPath("$.predios", hasSize(1)))
                .andExpect(jsonPath("$.predios[0].codigoPredio", is("PRD-014502-01")))
                .andExpect(jsonPath("$.deuda.puedeSolicitar", is(true)))
                .andExpect(jsonPath("$.deuda.total", is(0)));

        verify(constanciaVentanillaService).consultar(DNI);
    }

    @Test
    void emitirConstanciaVentanillaRetorna201YDatosDeConstancia() throws Exception {
        LocalDateTime emision = LocalDateTime.of(2026, 10, 19, 10, 30, 0);
        ConstanciaVentanillaRespuesta respuesta = new ConstanciaVentanillaRespuesta(
                "CNA-2026-000001", 101L, DNI, "GARCÍA TORRES, MARÍA ELENA", "014502",
                emision, "PRD-014502-01", "Periodo 2024 - 2026", TRABAJADOR, "Ventanilla de Pruebas",
                "VENTANILLA", "EMITIDA", "CONST-VENT-55", BigDecimal.valueOf(10.00), true);

        when(constanciaVentanillaService.emitir(any(EmitirConstanciaVentanillaSolicitud.class), eq(TRABAJADOR)))
                .thenReturn(respuesta);

        Principal principal = () -> TRABAJADOR;

        String payload = """
                {
                    "documentoContribuyente": "%s",
                    "predios": ["PRD-014502-01"],
                    "rangoFechas": "Periodo 2024 - 2026"
                }
                """.formatted(DNI);

        mockMvc.perform(post(RUTA_VENTANILLA + "/emitir")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroConstancia", is("CNA-2026-000001")))
                .andExpect(jsonPath("$.documentoContribuyente", is(DNI)))
                .andExpect(jsonPath("$.canal", is("VENTANILLA")))
                .andExpect(jsonPath("$.estado", is("EMITIDA")))
                .andExpect(jsonPath("$.trabajadorEmisor", is(TRABAJADOR)))
                .andExpect(jsonPath("$.prediosIncluidos", is("PRD-014502-01")))
                .andExpect(jsonPath("$.rangoFechas", is("Periodo 2024 - 2026")))
                .andExpect(jsonPath("$.montoDerechoTramite", is(10.0)));

        verify(constanciaVentanillaService).emitir(any(EmitirConstanciaVentanillaSolicitud.class), eq(TRABAJADOR));
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
