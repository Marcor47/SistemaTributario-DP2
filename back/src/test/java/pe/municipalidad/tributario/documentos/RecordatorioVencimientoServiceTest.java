package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracionRepository;

class RecordatorioVencimientoServiceTest {
    @Test
    void procesaSoloElIntervaloConfiguradoYOmiteDuplicadosYSinCorreo() {
        var samnet = mock(SamnetGateway.class);
        var avisos = mock(AvisoCorreoRepository.class);
        var correo = mock(CorreoGateway.class);
        var jdbc = mock(JdbcTemplate.class);
        var parametros = mock(ParametroConfiguracionRepository.class);
        var reloj = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Lima"));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyLong())).thenReturn(true);
        when(parametros.findById("DIAS_RECORDATORIO")).thenReturn(Optional.empty());
        when(samnet.obtenerVencimientos(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17)))
                .thenReturn(List.of(
                        Map.of("id_obligacion", 1L, "numero_documento", "41028765", "correo", "prueba@example.org",
                                "fecha_vencimiento", "2026-11-15", "saldo", "100.00"),
                        Map.of("id_obligacion", 2L, "numero_documento", "41028765", "correo", "prueba@example.org"),
                        Map.of("id_obligacion", 3L, "numero_documento", "41028765")));
        when(avisos.existsByTipoAndIdObligacionAndEstado(TipoAviso.RECORDATORIO_VENCIMIENTO, "2", EstadoAviso.ENVIADO))
                .thenReturn(true);
        var servicio = new RecordatorioVencimientoService(samnet, avisos, correo, jdbc, reloj, parametros);
        var resultado = servicio.procesar();
        assertThat(resultado.detectados()).isEqualTo(3);
        assertThat(resultado.enviados()).isEqualTo(1);
        assertThat(resultado.duplicados()).isEqualTo(1);
        assertThat(resultado.sinCorreo()).isEqualTo(1);
        verify(correo, times(1)).enviarCorreo(eq("prueba@example.org"), anyString(), contains("100.00"));
        verify(avisos, times(1)).save(any(AvisoCorreo.class));
    }
}
