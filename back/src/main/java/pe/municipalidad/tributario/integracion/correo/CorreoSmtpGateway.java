package pe.municipalidad.tributario.integracion.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/*
 * Gateway SMTP real para el envío de avisos por correo (RF-013, RF-022, HU012 y HU016).
 * Se activa cuando tributario.correo.modo=smtp (variable CORREO_MODO).
 * Si el modo no es smtp, Spring usa CorreoRegistroLocalGateway.
 * El HTML se envía como multipart/alternative con su texto plano de respaldo.
 */
@Component
@ConditionalOnProperty(name = "tributario.correo.modo", havingValue = "smtp")
public class CorreoSmtpGateway implements CorreoGateway {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoSmtpGateway.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String nombreRemitente;

    public CorreoSmtpGateway(JavaMailSender mailSender,
            @Value("${tributario.correo.remitente:}") String remitente,
            @Value("${tributario.correo.nombre-remitente:}") String nombreRemitente) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.strip();
        this.nombreRemitente = nombreRemitente == null ? "" : nombreRemitente.strip();
    }

    @Override
    public void enviarCorreo(String destino, String asunto, String cuerpo) {
        enviarCorreo(destino, asunto, cuerpo, null);
    }

    @Override
    public void enviarCorreo(String destino, String asunto, String cuerpo, String html) {
        if (remitente.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar CORREO_REMITENTE para enviar los avisos por correo real.");
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            if (nombreRemitente.isBlank()) {
                helper.setFrom(remitente);
            } else {
                helper.setFrom(remitente, nombreRemitente);
            }
            helper.setTo(destino);
            helper.setSubject(asunto);
            if (html == null || html.isBlank()) {
                helper.setText(cuerpo, false);
            } else {
                /*
                 * Se arma directamente multipart/alternative para que el cliente muestre el HTML y use el
                 * texto plano como respaldo. No se agrega el envoltorio mixed/related que usa el helper
                 * cuando se habilitan adjuntos o imágenes inline.
                 */
                MimeMultipart alternativa = new MimeMultipart("alternative");
                MimeBodyPart texto = new MimeBodyPart();
                texto.setText(cuerpo, "UTF-8");
                MimeBodyPart htmlParte = new MimeBodyPart();
                htmlParte.setContent(html, "text/html; charset=UTF-8");
                alternativa.addBodyPart(texto);
                alternativa.addBodyPart(htmlParte);
                mensaje.setContent(alternativa);
            }
            mailSender.send(mensaje);
            LOG.info("Correo enviado por SMTP: destino='{}', asunto='{}'", enmascarar(destino), asunto);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            LOG.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, enmascarar(destino), ex.getMessage());
            throw new IllegalStateException(
                    "No se pudo enviar el correo a " + enmascarar(destino) + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean registraSinEnviar() {
        return false;
    }

    private static String enmascarar(String correo) {
        if (correo == null || correo.isBlank()) return "(sin destino)";
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
