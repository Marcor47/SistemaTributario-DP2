package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.municipalidad.tributario.common.DatoInvalidoException;
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

    /* El estado previo se arma con las reglas del dominio, sin pasar por el servicio que se prueba. */
    private SolicitudConstancia solicitudEn(EstadoConstancia estado) {
        return solicitudEn(estado, true);
    }

    private SolicitudConstancia solicitudEn(EstadoConstancia estado, boolean conDeudaPresentacion) {
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.VENTANILLA, DNI_SIN_DEUDA, trabajador,
                AHORA);
        if (conDeudaPresentacion) {
            solicitud.registrarDeudaPresentacion(BigDecimal.ZERO, AHORA);
        }
        if (estado == EstadoConstancia.INICIADA) {
            return solicitud;
        }
        solicitud.registrarFirma(new FirmaDigital(TipoFirmante.CONTRIBUYENTE, "ENTIDAD DE PRUEBA", AHORA, true,
                "a".repeat(64)));
        if (estado == EstadoConstancia.FIRMADA) {
            return solicitud;
        }
        solicitud.verificarDeuda(trabajador, BigDecimal.ZERO, AHORA);
        TransaccionPago orden = ordenDe(solicitud);
        if (estado == EstadoConstancia.ORDEN_PAGO_EMITIDA) {
            solicitud.emitirOrdenPago(orden);
            return solicitud;
        }
        orden.confirmar("CONST-VENT-1");
        solicitud.emitirOrdenPago(orden);
        solicitud.confirmarPago(orden);
        return solicitud;
    }

    private TransaccionPago ordenDe(SolicitudConstancia solicitud) {
        return TransaccionPago.deDerechoTramite(solicitud, BigDecimal.valueOf(10.00), AHORA);
    }

    private void trabajadorActivo() {
        when(usuarioRepository.findByNombreUsuario(USUARIO_VENTANILLA)).thenReturn(Optional.of(trabajador));
    }

    private void contribuyenteEnSamnet() {
        when(samnet.buscarContribuyente(DNI_SIN_DEUDA)).thenReturn(List.of(contribuyenteSinDeuda));
    }

    private void deudaCero() {
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.ZERO, List.of()));
    }

    @Test
    void consultarRetornaContribuyentePrediosYEstadoDeudaCero() {
        contribuyenteEnSamnet();
        Predio predio = new Predio("PRD-001", "CATASTRO-100", "Av. La Marina 1500", "VIVIENDA",
                TipoPredio.URBANO, BigDecimal.valueOf(150), BigDecimal.valueOf(80000),
                LocalDate.of(2015, 5, 10), null, "ZONA 1", List.of());
        when(samnet.buscarPredio(null, CODIGO_SIN_DEUDA)).thenReturn(List.of(predio));
        deudaCero();

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
    void crearSolicitudQuedaEnCanalVentanillaConElTrabajadorQueAtiende() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        deudaCero();
        when(solicitudConstanciaRepository
                .findFirstByDocumentoContribuyenteAndCanalAndEstadoNotInOrderByFechaSolicitudDesc(
                        eq(DNI_SIN_DEUDA), eq(CanalSolicitud.VENTANILLA), any()))
                .thenReturn(Optional.empty());
        when(solicitudConstanciaRepository.save(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.crearSolicitud(DNI_SIN_DEUDA, USUARIO_VENTANILLA);

        assertThat(respuesta.canal()).isEqualTo(CanalSolicitud.VENTANILLA);
        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.INICIADA);
        assertThat(respuesta.documentoContribuyente()).isEqualTo(DNI_SIN_DEUDA);
        assertThat(respuesta.nombreContribuyente()).isEqualTo("GARCÍA TORRES, MARÍA ELENA");
        assertThat(respuesta.codigoContribuyente()).isEqualTo(CODIGO_SIN_DEUDA);
        assertThat(respuesta.ordenPago()).isNull();
        assertThat(respuesta.deuda()).isNotNull();
        assertThat(respuesta.deuda().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void crearSolicitudConDeudaQuedaRechazadaConElDetalle() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        when(solicitudConstanciaRepository
                .findFirstByDocumentoContribuyenteAndCanalAndEstadoNotInOrderByFechaSolicitudDesc(
                        eq(DNI_SIN_DEUDA), eq(CanalSolicitud.VENTANILLA), any()))
                .thenReturn(Optional.empty());
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.valueOf(150.50), List.of()));
        when(solicitudConstanciaRepository.save(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.crearSolicitud(DNI_SIN_DEUDA, USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.RECHAZADA);
        assertThat(respuesta.motivoRechazo()).contains("deuda pendiente");
        assertThat(respuesta.deuda().total()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
        assertThat(respuesta.ordenPago()).isNull();
        verify(transaccionPagoRepository, never()).save(any());
    }

    @Test
    void crearSolicitudRetomaLaAbiertaEnLugarDeDuplicar() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        SolicitudConstancia abierta = solicitudEn(EstadoConstancia.FIRMADA);
        when(solicitudConstanciaRepository
                .findFirstByDocumentoContribuyenteAndCanalAndEstadoNotInOrderByFechaSolicitudDesc(
                        eq(DNI_SIN_DEUDA), eq(CanalSolicitud.VENTANILLA), any()))
                .thenReturn(Optional.of(abierta));

        SolicitudVentanillaRespuesta respuesta = servicio.crearSolicitud(DNI_SIN_DEUDA, USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.FIRMADA);
        verify(solicitudConstanciaRepository, never()).save(any());
        verify(solicitudConstanciaService, never()).consultarDeuda(any());
    }

    @Test
    void listarSolicitudesDevuelveAmbosCanalesYTodosLosEstadosSinRepetirSamnet() {
        contribuyenteEnSamnet();
        SolicitudConstancia ventanilla = solicitudEn(EstadoConstancia.INICIADA);
        SolicitudConstancia app = new SolicitudConstancia(CanalSolicitud.APP, DNI_SIN_DEUDA, null, AHORA);
        app.registrarFirma(new FirmaDigital(TipoFirmante.CONTRIBUYENTE, "ENTIDAD DE PRUEBA", AHORA, true,
                "c".repeat(64)));
        when(solicitudConstanciaRepository.findTop20ByDocumentoContribuyenteOrderByFechaSolicitudDesc(DNI_SIN_DEUDA))
                .thenReturn(List.of(app, ventanilla));
        when(transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(any())).thenReturn(Optional.empty());
        when(constanciaRepository.findBySolicitud_IdSolicitud(any())).thenReturn(Optional.empty());

        List<SolicitudVentanillaRespuesta> respuesta = servicio.listarSolicitudes(DNI_SIN_DEUDA);

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta).extracting(SolicitudVentanillaRespuesta::canal)
                .containsExactly(CanalSolicitud.APP, CanalSolicitud.VENTANILLA);
        assertThat(respuesta).extracting(SolicitudVentanillaRespuesta::estado)
                .containsExactly(EstadoConstancia.FIRMADA, EstadoConstancia.INICIADA);
        assertThat(respuesta.get(1).nombreContribuyente()).isEqualTo("GARCÍA TORRES, MARÍA ELENA");
        verify(samnet, times(1)).buscarContribuyente(DNI_SIN_DEUDA);
    }

    @Test
    void listarSolicitudesIncluyeLaConstanciaDeLaSolicitudEmitida() {
        contribuyenteEnSamnet();
        SolicitudConstancia emitida = solicitudEn(EstadoConstancia.PAGADA);
        emitida.emitir(trabajador, new FirmaDigital(TipoFirmante.MUNICIPALIDAD, "MUNICIPALIDAD", AHORA, true,
                "d".repeat(64)), AHORA);
        when(solicitudConstanciaRepository.findTop20ByDocumentoContribuyenteOrderByFechaSolicitudDesc(DNI_SIN_DEUDA))
                .thenReturn(List.of(emitida));
        when(transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(any())).thenReturn(Optional.empty());
        when(constanciaRepository.findBySolicitud_IdSolicitud(any()))
                .thenReturn(Optional.of(new Constancia("CNA-2026-000001", emitida, null, AHORA, "Todos",
                        "Hasta emisión", "CNA-2026-000001.pdf", true, trabajador, new byte[0])));

        List<SolicitudVentanillaRespuesta> respuesta = servicio.listarSolicitudes(DNI_SIN_DEUDA);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).estado()).isEqualTo(EstadoConstancia.EMITIDA);
        assertThat(respuesta.get(0).numeroConstancia()).isEqualTo("CNA-2026-000001");
    }

    @Test
    void listarSolicitudesSinRegistrosDevuelveListaVacia() {
        contribuyenteEnSamnet();
        when(solicitudConstanciaRepository.findTop20ByDocumentoContribuyenteOrderByFechaSolicitudDesc(DNI_SIN_DEUDA))
                .thenReturn(List.of());

        assertThat(servicio.listarSolicitudes(DNI_SIN_DEUDA)).isEmpty();
    }

    @Test
    void obtenerDocumentoFirmaDevuelveElDocumentoGeneradoPorElServidor() {
        byte[] documento = "SOLICITUD DE CONSTANCIA DE NO ADEUDO".getBytes();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.INICIADA);
        when(solicitudConstanciaRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        contribuyenteEnSamnet();
        when(solicitudConstanciaService.documentoParaFirma(solicitud)).thenReturn(documento);
        when(firmador.esSimulado()).thenReturn(true);

        DocumentoFirmaRespuesta respuesta = servicio.obtenerDocumentoFirma(1L);

        assertThat(respuesta.documentoDePrueba()).isTrue();
        assertThat(Base64.getDecoder().decode(respuesta.contenidoBase64())).isEqualTo(documento);
        assertThat(respuesta.hashSha256()).hasSize(64);
        assertThat(respuesta.datosContribuyente().numeroDocumento()).isEqualTo(DNI_SIN_DEUDA);
    }

    @Test
    void obtenerDocumentoFirmaExigeQueLaSolicitudEstePendienteDeFirmar() {
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.FIRMADA);
        when(solicitudConstanciaRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> servicio.obtenerDocumentoFirma(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("INICIADA");
    }

    @Test
    void registrarFirmaValidaContraElDocumentoDelServidorYPasaAFirmada() {
        byte[] documento = "SOLICITUD DE CONSTANCIA DE NO ADEUDO".getBytes();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.INICIADA);
        trabajadorActivo();
        when(solicitudConstanciaRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        contribuyenteEnSamnet();
        when(solicitudConstanciaService.documentoParaFirma(solicitud)).thenReturn(documento);
        when(firmador.verificarFirma(eq(documento), any(), eq("CERTIFICADO DE PRUEBA")))
                .thenReturn(new FirmadorGateway.ResultadoFirma(true, "ENTIDAD DE PRUEBA", "hash-documento", null));
        when(solicitudConstanciaRepository.saveAndFlush(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.registrarFirma(1L,
                new FirmaSolicitudConstancia("ZklJTUE=", "CERTIFICADO DE PRUEBA"), USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.FIRMADA);
        assertThat(respuesta.firmaContribuyente()).isNotNull();
        assertThat(respuesta.firmaContribuyente().hashDocumento()).isEqualTo("hash-documento");
    }

    @Test
    void registrarFirmaInvalidaNoAvanzaLaSolicitud() {
        byte[] documento = "SOLICITUD DE CONSTANCIA DE NO ADEUDO".getBytes();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.INICIADA);
        trabajadorActivo();
        when(solicitudConstanciaRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudConstanciaService.documentoParaFirma(solicitud)).thenReturn(documento);
        when(firmador.verificarFirma(eq(documento), any(), eq("VENCIDO")))
                .thenReturn(new FirmadorGateway.ResultadoFirma(false, null, "hash-documento",
                        "el certificado digital está vencido"));

        assertThatThrownBy(() -> servicio.registrarFirma(1L,
                new FirmaSolicitudConstancia("ZklJTUE=", "VENCIDO"), USUARIO_VENTANILLA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("certificado digital está vencido");
    }

    @Test
    void verificarDeudaConDeudaCeroEmiteLaOrdenDePagoSinConsultarSamnet() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.FIRMADA);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));
        when(transaccionPagoRepository.save(any(TransaccionPago.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(solicitudConstanciaRepository.saveAndFlush(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.verificarDeuda(1L, USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        assertThat(respuesta.ordenPago()).isNotNull();
        assertThat(respuesta.ordenPago().monto()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(respuesta.ordenPago().estado()).isEqualTo(EstadoTransaccion.INICIADA);
        assertThat(respuesta.deuda().puedeSolicitar()).isTrue();
        verify(solicitudConstanciaService, never()).consultarDeuda(any());
    }

    @Test
    void verificarSolicitudAntiguaSinEvaluacionHaceUnUnicoRespaldoYRechaza() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.FIRMADA, false);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudConstanciaService.consultarDeuda(CODIGO_SIN_DEUDA))
                .thenReturn(new DeudaConstanciaRespuesta(BigDecimal.valueOf(150.50), List.of()));
        when(solicitudConstanciaRepository.saveAndFlush(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.verificarDeuda(1L, USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.RECHAZADA);
        assertThat(respuesta.motivoRechazo()).contains("deuda pendiente");
        assertThat(respuesta.ordenPago()).isNull();
        assertThat(respuesta.deuda().total()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
        assertThat(solicitud.getDeudaPresentacion()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
    }

    @Test
    void confirmarPagoPresencialDejaLaSolicitudPagadaConComprobante() {
        trabajadorActivo();
        contribuyenteEnSamnet();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));
        TransaccionPago orden = ordenDe(solicitud);
        when(transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(1L)).thenReturn(Optional.of(orden));
        when(transaccionPagoRepository.saveAndFlush(any(TransaccionPago.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(solicitudConstanciaRepository.saveAndFlush(any(SolicitudConstancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SolicitudVentanillaRespuesta respuesta = servicio.confirmarPago(1L, USUARIO_VENTANILLA);

        assertThat(respuesta.estado()).isEqualTo(EstadoConstancia.PAGADA);
        assertThat(respuesta.ordenPago().estado()).isEqualTo(EstadoTransaccion.CONFIRMADA);
        assertThat(respuesta.ordenPago().comprobante()).startsWith("CONST-VENT-");
        assertThat(respuesta.ordenPago().monto()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    @Test
    void emitirConstanciaGeneraElPdfFirmadoYLoEnviaAlBuzon() {
        trabajadorActivo();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.PAGADA);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));
        contribuyenteEnSamnet();

        VersionPlantilla plantilla = org.mockito.Mockito.mock(VersionPlantilla.class);
        when(versionPlantillaRepository.findByPlantillaCodigoAndVigenteTrue("CONSTANCIA_NO_ADEUDO"))
                .thenReturn(Optional.of(plantilla));

        when(constanciaRepository.siguienteNumero(AHORA.toLocalDate())).thenReturn("CNA-2026-000001");

        byte[] pdfBytes = "PDF SIMULADO".getBytes();
        when(generadorPdf.generar(eq("CNA-2026-000001"), eq(contribuyenteSinDeuda), any(LocalDateTime.class),
                eq(AHORA.toLocalDate()), eq("PRD-001, PRD-002"), eq("2024 - 2026"),
                eq("Ventanilla de Pruebas"), eq(true)))
                .thenReturn(pdfBytes);

        FirmadorGateway.ResultadoFirma resultadoFirma = new FirmadorGateway.ResultadoFirma(
                true, "ENTIDAD DE PRUEBA", "hash-municipal", null);
        when(firmador.firmarComoMunicipalidad(pdfBytes))
                .thenReturn(new FirmadorGateway.DocumentoFirmado(pdfBytes, resultadoFirma));
        when(firmador.esSimulado()).thenReturn(true);

        when(constanciaRepository.save(any(Constancia.class))).thenAnswer(inv -> inv.getArgument(0));
        TransaccionPago orden = ordenDe(solicitud);
        orden.confirmar("CONST-VENT-1");
        when(transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(1L)).thenReturn(Optional.of(orden));

        EmitirConstanciaVentanillaSolicitud peticion = new EmitirConstanciaVentanillaSolicitud(
                List.of("PRD-001", "PRD-002"), null, "2024 - 2026");

        ConstanciaVentanillaRespuesta respuesta = servicio.emitir(1L, peticion, USUARIO_VENTANILLA);

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
        verify(solicitudConstanciaService, never()).consultarDeuda(any());
    }

    @Test
    void emitirSinPagoConfirmadoEsRechazado() {
        trabajadorActivo();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> servicio.emitir(1L,
                new EmitirConstanciaVentanillaSolicitud(null, null, null), USUARIO_VENTANILLA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("pago del trámite");
    }

    @Test
    void emitirSinEvaluacionDeDeudaAlPresentarEsRechazado() {
        trabajadorActivo();
        SolicitudConstancia solicitud = solicitudEn(EstadoConstancia.PAGADA, false);
        when(solicitudConstanciaRepository.bloquearPorId(1L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> servicio.emitir(1L,
                new EmitirConstanciaVentanillaSolicitud(null, null, null), USUARIO_VENTANILLA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("evaluación sin adeudo");
        verify(solicitudConstanciaService, never()).consultarDeuda(any());
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
