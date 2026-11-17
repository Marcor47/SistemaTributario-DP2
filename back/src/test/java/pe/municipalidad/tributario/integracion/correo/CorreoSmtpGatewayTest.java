package pe.municipalidad.tributario.integracion.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class CorreoSmtpGatewayTest {

    @Test
    void enviaHtmlYTextoPlanoConRemitenteYDestino() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        CorreoSmtpGateway gateway = new CorreoSmtpGateway(mailSender, "no-responder@example.org",
                "Municipalidad Distrital de San Miguel");

        gateway.enviarCorreo("contribuyente@example.org", "Recordatorio de vencimiento",
                "Texto plano del recordatorio", "<html><body>Hola</body></html>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage mensaje = captor.getValue();
        mensaje.saveChanges();
        assertThat(mensaje.getSubject()).isEqualTo("Recordatorio de vencimiento");
        assertThat(mensaje.getFrom()[0].toString()).contains("no-responder@example.org");
        assertThat(mensaje.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("contribuyente@example.org");
        assertThat(mensaje.getContentType()).startsWith("multipart/alternative");

        java.util.List<String> textos = new java.util.ArrayList<>();
        java.util.List<String> htmls = new java.util.ArrayList<>();
        recorrer((Multipart) mensaje.getContent(), textos, htmls);
        assertThat(textos).anyMatch(texto -> texto.contains("Texto plano del recordatorio"));
        assertThat(htmls).anyMatch(html -> html.contains("Hola"));
    }

    private static void recorrer(Multipart multipart, java.util.List<String> textos,
            java.util.List<String> htmls) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart parte = multipart.getBodyPart(i);
            Object contenido = parte.getContent();
            if (contenido instanceof Multipart anidado) {
                recorrer(anidado, textos, htmls);
            } else if (parte.isMimeType("text/plain")) {
                textos.add((String) contenido);
            } else if (parte.isMimeType("text/html")) {
                htmls.add((String) contenido);
            }
        }
    }

    @Test
    void sinRemitenteNoIntentaEnviar() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        CorreoSmtpGateway gateway = new CorreoSmtpGateway(mailSender, " ", "Municipalidad");

        assertThatThrownBy(() -> gateway.enviarCorreo("contribuyente@example.org", "Asunto", "Cuerpo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORREO_REMITENTE");
    }

    @Test
    void unFalloDelServidorSePropagaParaMarcarElAvisoComoFallido() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP no disponible")).when(mailSender).send(any(MimeMessage.class));
        CorreoSmtpGateway gateway = new CorreoSmtpGateway(mailSender, "no-responder@example.org", "Municipalidad");

        assertThatThrownBy(() -> gateway.enviarCorreo("contribuyente@example.org", "Asunto", "Cuerpo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo enviar");
    }
}
