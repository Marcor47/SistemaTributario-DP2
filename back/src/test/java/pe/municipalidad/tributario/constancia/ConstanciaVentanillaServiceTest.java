package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ConstanciaVentanillaServiceTest {

    private static final String DNI_SIN_DEUDA = "41028765";
    private static final String CODIGO_SIN_DEUDA = "014502";
    private static final String USUARIO_VENTANILLA = "ventanilla";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 10, 0, 0);

    @Mock
    private SolicitudConstanciaRepository solicitudConstanciaRepository;

    @Mock
    private SolicitudConstanciaService solicitudConstanciaService;

    @Mock
    private TransaccionPagoRepository transaccionPagoRepository;

    @Mock
    private ConstanciaRepository constanciaRepository;

    @Mock
    private DocumentoBuzonRepository documentoBuzonRepository;

    @Mock
    private VersionPlantillaRepository versionPlantillaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SamnetGateway samnet;

    @Mock
    private FirmadorGateway firmador;

    @Mock
    private GeneradorConstanciaPdf generadorPdf;

    private Clock reloj;
    private ConstanciaVentanillaService servicio;
    private Contribuyente contribuyenteSinDeuda;
    private Usuario trabajador;

    @BeforeEach
    void setUp() {
        reloj = Clock.fixed(AHORA.atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"));
        servicio = new ConstanciaVentanillaService(
                solicitudConstanciaRepository,
                solicitudConstanciaService,
                transaccionPagoRepository,
                constanciaRepository,
                documentoBuzonRepository,
                versionPlantillaRepository,
                usuarioRepository,
                samnet,
                firmador,
                generadorPdf,
                reloj,
                BigDecimal.valueOf(10.00));

        contribuyenteSinDeuda = new Contribuyente(CODIGO_SIN_DEUDA, TipoDocumento.DNI, DNI_SIN_DEUDA,
                "GARCÍA TORRES, MARÍA ELENA", "GENERAL", pe.municipalidad.tributario.contribuyente.TipoPersona.NATURAL,
                "HABIDO", "maria@example.org", "999888777",
                pe.municipalidad.tributario.contribuyente.EstadoContribuyente.ACTIVO, null);

        pe.municipalidad.tributario.seguridad.Rol rolVentanilla =
                new pe.municipalidad.tributario.seguridad.Rol(pe.municipalidad.tributario.seguridad.NombreRol.VENTANILLA,
                        "Rol Ventanilla", "CONSTANCIAS:GESTIONAR");
        trabajador = new Usuario(USUARIO_VENTANILLA, "Ventanilla de Pruebas", "ventanilla@sm.gob.pe",
                "$2a$12$hashPrueba", rolVentanilla, AHORA);
    }

    @Test
    void consultarRetornaContribuyentePrediosYEstadoDeudaCero() {
        when(samnet.buscarContribuyente(DNI_SIN_DEUDA)).thenReturn(List.of(contribuyenteSinDeuda));
        Predio predio = new Predio("PRD-001", "CATASTRO-100", "Av. La Marina 1500", "VIVIENDA",
                TipoPredio.URBANO, BigDecimal.valueOf(150), BigDecimal.valueOf(80000),
                LocalDate.of(2015, 5, 10), null, "ZONA 1", List.of());
        when(samnet.buscarPredio(null, CODIGO_SIN_DEUDA)).thenReturn(List.of(predio));
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.ZERO, List.of()));

        ConsultaContribuyenteVentanillaRespuesta respuesta = servicio.consultar(DNI_SIN_DEUDA);

        assertThat(respuesta.contribuyente().numeroDocumento()).isEqualTo(DNI_SIN_DEUDA);
        assertThat(respuesta.contribuyente().nombreRazonSocial()).isEqualTo("GARCÍA TORRES, MARÍA ELENA");
        assertThat(respuesta.predios()).hasSize(1);
        assertThat(respuesta.predios().get(0).codigoPredio()).isEqualTo("PRD-001");
        assertThat(respuesta.deuda().puedeSolicitar()).isTrue();
        assertThat(respuesta.deuda().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consultarConDocumentoInvalidoLanzaExcepcion() {
        assertThatThrownBy(() -> servicio.consultar("  "))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessageContaining("DNI o documento");
    }

    @Test
    void consultarContribuyenteNoEncontradoLanzaRecursoNoEncontrado() {
        when(samnet.buscarContribuyente("00000000")).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.consultar("00000000"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No se encontró un contribuyente");
    }

    @Test
    void emitirEnVentanillaFlujoExitosoConFirmaMunicipalYTrabajadorRegistrado() {
        when(usuarioRepository.findByNombreUsuario(USUARIO_VENTANILLA)).thenReturn(Optional.of(trabajador));
        when(samnet.buscarContribuyente(DNI_SIN_DEUDA)).thenReturn(List.of(contribuyenteSinDeuda));
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.ZERO, List.of()));

        VersionPlantilla plantilla = org.mockito.Mockito.mock(VersionPlantilla.class);
        when(versionPlantillaRepository.findByPlantillaCodigoAndVigenteTrue("CONSTANCIA_NO_ADEUDO"))
                .thenReturn(Optional.of(plantilla));

        when(solicitudConstanciaRepository.save(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(solicitudConstanciaRepository.saveAndFlush(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(transaccionPagoRepository.save(any(TransaccionPago.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(constanciaRepository.siguienteNumero(AHORA.toLocalDate())).thenReturn("CNA-2026-000001");

        byte[] pdfBytes = "PDF SIMULADO".getBytes();
        when(generadorPdf.generar(eq("CNA-2026-000001"), eq(contribuyenteSinDeuda), any(LocalDateTime.class),
                eq("PRD-001, PRD-002"), eq("2024 - 2026"), eq("Ventanilla de Pruebas"), eq(true)))
                .thenReturn(pdfBytes);

        FirmadorGateway.ResultadoFirma resultadoFirma = new FirmadorGateway.ResultadoFirma(
                true, null, "hash-documento-sha256", "RENIEC - Municipalidad");
        when(firmador.firmarComoMunicipalidad(pdfBytes))
                .thenReturn(new FirmadorGateway.DocumentoFirmado(pdfBytes, resultadoFirma));
        when(firmador.esSimulado()).thenReturn(true);

        when(constanciaRepository.save(any(Constancia.class))).thenAnswer(inv -> inv.getArgument(0));

        EmitirConstanciaVentanillaSolicitud peticion = new EmitirConstanciaVentanillaSolicitud(
                DNI_SIN_DEUDA,
                List.of("PRD-001", "PRD-002"),
                null,
                "2024 - 2026");

        ConstanciaVentanillaRespuesta respuesta = servicio.emitir(peticion, USUARIO_VENTANILLA);

        assertThat(respuesta.numeroConstancia()).isEqualTo("CNA-2026-000001");
        assertThat(respuesta.documentoContribuyente()).isEqualTo(DNI_SIN_DEUDA);
        assertThat(respuesta.canal()).isEqualTo("VENTANILLA");
        assertThat(respuesta.estado()).isEqualTo("EMITIDA");
        assertThat(respuesta.trabajadorEmisor()).isEqualTo(USUARIO_VENTANILLA);
        assertThat(respuesta.nombreTrabajadorEmisor()).isEqualTo("Ventanilla de Pruebas");
        assertThat(respuesta.prediosIncluidos()).isEqualTo("PRD-001, PRD-002");
        assertThat(respuesta.rangoFechas()).isEqualTo("2024 - 2026");
        assertThat(respuesta.comprobantePago()).startsWith("CONST-VENT-");
        assertThat(respuesta.montoDerechoTramite()).isEqualByComparingTo(BigDecimal.valueOf(10.00));

        verify(documentoBuzonRepository).save(any(DocumentoBuzon.class));
    }

    @Test
    void emitirConDeudaPendienteEsRechazadoYNoGeneraConstancia() {
        when(usuarioRepository.findByNombreUsuario(USUARIO_VENTANILLA)).thenReturn(Optional.of(trabajador));
        when(samnet.buscarContribuyente(DNI_SIN_DEUDA)).thenReturn(List.of(contribuyenteSinDeuda));
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.valueOf(150.50), List.of()));

        EmitirConstanciaVentanillaSolicitud peticion = new EmitirConstanciaVentanillaSolicitud(
                DNI_SIN_DEUDA, null, null, null);

        assertThatThrownBy(() -> servicio.emitir(peticion, USUARIO_VENTANILLA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("deuda pendiente");
    }

    @Test
    void obtenerPdfDevuelveContenidoSiExiste() {
        Constancia constancia = new Constancia("CNA-2026-000001", null, null, AHORA,
                "Todos", "Hasta emisión", "CNA-2026-000001.pdf", true, trabajador, "BYTES PDF".getBytes());
        when(constanciaRepository.findById("CNA-2026-000001")).thenReturn(Optional.of(constancia));

        byte[] pdf = servicio.obtenerPdf("CNA-2026-000001");
        assertThat(pdf).isEqualTo("BYTES PDF".getBytes());
    }

    @Test
    void obtenerPdfLanza404SiNoExisteConstancia() {
        when(constanciaRepository.findById("CNA-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPdf("CNA-9999"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No existe la constancia");
    }
}
