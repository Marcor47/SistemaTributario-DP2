package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;

class AvisoCorreoTest {

    @Test
    void unFalloDelEnvioDejaElAvisoFallidoParaReintentarlo() {
        CorreoGateway correo = mock(CorreoGateway.class);
        doThrow(new IllegalStateException("SMTP caído"))
                .when(correo).enviarCorreo(anyString(), anyString(), anyString(), anyString());
        AvisoCorreo aviso = new AvisoCorreo(TipoAviso.RECORDATORIO_VENCIMIENTO, "41028765",
                "contribuyente@example.org", LocalDateTime.now(), null, "1");

        aviso.enviar(correo, "Asunto", "Cuerpo", "<html></html>", LocalDateTime.now());

        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.FALLIDO);
    }
}
