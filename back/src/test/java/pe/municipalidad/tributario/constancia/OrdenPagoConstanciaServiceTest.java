package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

@ExtendWith(MockitoExtension.class)
class OrdenPagoConstanciaServiceTest {
    @Mock SolicitudConstanciaRepository solicitudes;
    @Mock TransaccionPagoRepository transacciones;
    @Mock ContribuyenteService contribuyentes;
    @Mock NiubizGateway pasarela;
    @Mock ConstanciaRepository constancias;
    @Mock pe.municipalidad.tributario.documentos.DocumentoBuzonRepository documentos;
    @Mock FirmaAutomaticaConstanciaService firmaAutomatica;
    @Mock SolicitudConstancia solicitud;
    @Mock TransaccionPago orden;
    @Mock Contribuyente contribuyente;
    OrdenPagoConstanciaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new OrdenPagoConstanciaService(solicitudes, transacciones, contribuyentes,
                constancias, documentos, pasarela, firmaAutomatica);
        when(contribuyentes.obtenerPorCodigo("014502")).thenReturn(contribuyente);
        when(contribuyente.getNumeroDocumento()).thenReturn("41028765");
        when(solicitudes.bloquearPorId(7L)).thenReturn(Optional.of(solicitud));
        when(solicitud.getDocumentoContribuyente()).thenReturn("41028765");
        when(solicitud.getIdSolicitud()).thenReturn(7L);
        when(solicitud.getEstado()).thenReturn(EstadoConstancia.ORDEN_PAGO_EMITIDA);
        when(transacciones.findBySolicitudConstancia_IdSolicitud(7L)).thenReturn(Optional.of(orden));
        when(orden.getEstado()).thenReturn(EstadoTransaccion.INICIADA);
    }

    @Test
    void usaIdGlobalDeTransaccionParaCrearLaSesion() {
        when(orden.getIdTransaccion()).thenReturn(123L);
        when(orden.getMontoTotal()).thenReturn(new BigDecimal("10.00"));
        when(pasarela.crearOrdenPago(any(), anyString(), any(), anyString()))
                .thenReturn(new NiubizGateway.OrdenPago("000000000123", null));

        servicio.iniciarPago("014502", 7L, "127.0.0.1");

        verify(pasarela).crearOrdenPago(eq(new BigDecimal("10.00")), eq("CONSTANCIA-123"),
                same(contribuyente), eq("127.0.0.1"));
        verify(orden).iniciar("000000000123");
    }

    @Test
    void noReemplazaUnaSesionQueYaSeInicio() {
        when(orden.getIdOrdenPasarela()).thenReturn("000000000123");

        assertThatThrownBy(() -> servicio.iniciarPago("014502", 7L, "127.0.0.1"))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getCodigo()).isEqualTo("PAGO_YA_INICIADO"));
        verifyNoInteractions(pasarela);
    }
}
