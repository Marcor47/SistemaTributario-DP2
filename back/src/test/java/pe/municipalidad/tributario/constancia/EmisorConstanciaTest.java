package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.EstadoContribuyente;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.Usuario;

@ExtendWith(MockitoExtension.class)
class EmisorConstanciaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 10, 30);

    @Mock ConstanciaRepository constancias;
    @Mock DocumentoBuzonRepository documentos;
    @Mock VersionPlantillaRepository plantillas;
    @Mock FirmadorGateway firmador;
    @Mock GeneradorConstanciaPdf generador;

    private EmisorConstancia emisor;

    @BeforeEach
    void setUp() {
        emisor = new EmisorConstancia(constancias, documentos, plantillas, firmador, generador);
    }

    @Test
    void emiteConPrediosFechasYTrabajadorUsandoElFlujoComun() {
        Usuario trabajador = new Usuario("ventanilla", "José Vargas", "jvargas@sm.gob.pe", "hash",
                new Rol(NombreRol.VENTANILLA, "Ventanilla", "CONSTANCIAS:GESTIONAR"), AHORA);
        SolicitudConstancia solicitud = solicitudPagada(trabajador);
        Contribuyente contribuyente = new Contribuyente("014502", TipoDocumento.DNI, "41028765",
                "MARÍA GARCÍA", "GENERAL", TipoPersona.NATURAL, "HABIDO", "maria@example.org", "999888777",
                EstadoContribuyente.ACTIVO, null);
        VersionPlantilla plantilla = mock(VersionPlantilla.class);
        byte[] pdf = "PDF".getBytes();
        FirmadorGateway.ResultadoFirma firma = new FirmadorGateway.ResultadoFirma(
                true, "MUNICIPALIDAD", "hash-documento", null);

        when(plantillas.findByPlantillaCodigoAndVigenteTrue(EmisorConstancia.CODIGO_PLANTILLA))
                .thenReturn(java.util.Optional.of(plantilla));
        when(constancias.siguienteNumero(AHORA.toLocalDate())).thenReturn("CND-2026-ABC234");
        when(firmador.firmaLocal()).thenReturn(true);
        when(generador.generar("CND-2026-ABC234", contribuyente, AHORA,
                solicitud.getFechaSolicitud().toLocalDate(), "PRD-001", "Del 01/01/2024 al 31/12/2026",
                "José Vargas", true)).thenReturn(pdf);
        when(firmador.firmarComoMunicipalidad(pdf)).thenReturn(new FirmadorGateway.DocumentoFirmado(pdf, firma));
        when(constancias.save(any(Constancia.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentos.save(any(DocumentoBuzon.class))).thenAnswer(inv -> inv.getArgument(0));

        EmisorConstancia.ConstanciaEmitida resultado = emisor.emitir(solicitud, contribuyente, trabajador, AHORA,
                "PRD-001", "Del 01/01/2024 al 31/12/2026");

        assertThat(resultado.constancia().getNumeroConstancia()).isEqualTo("CND-2026-ABC234");
        assertThat(resultado.constancia().getPrediosIncluidos()).isEqualTo("PRD-001");
        assertThat(resultado.constancia().getRangoFechas()).isEqualTo("Del 01/01/2024 al 31/12/2026");
        assertThat(resultado.constancia().getTrabajadorEmisor()).isSameAs(trabajador);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoConstancia.EMITIDA);
        verify(documentos).save(any(DocumentoBuzon.class));
    }

    private static SolicitudConstancia solicitudPagada(Usuario trabajador) {
        SolicitudConstancia solicitud = new SolicitudConstancia(
                CanalSolicitud.VENTANILLA, "41028765", trabajador, AHORA.minusDays(1));
        solicitud.registrarDeudaPresentacion(BigDecimal.ZERO, AHORA.minusDays(1));
        solicitud.registrarFirma(new FirmaDigital(TipoFirmante.CONTRIBUYENTE, "CERTIFICADORA",
                AHORA.minusDays(1), true, "a".repeat(64)));
        solicitud.verificarDeuda(trabajador, BigDecimal.ZERO, AHORA.minusHours(2));
        TransaccionPago orden = TransaccionPago.deDerechoTramite(solicitud, BigDecimal.TEN, AHORA.minusHours(1));
        solicitud.emitirOrdenPago(orden);
        orden.confirmar("CONST-VENT-1");
        solicitud.confirmarPago(orden);
        return solicitud;
    }
}
