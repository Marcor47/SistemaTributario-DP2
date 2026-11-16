package pe.municipalidad.tributario.integracion.correo;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/*
 * Gateway SMTP real para el envío de avisos por correo (RF-013, RF-022).
 * Se activa cuando CORREO_MODO=smtp está definido en el entorno.
 * Si la variable no está presente o tiene otro valor, Spring usa CorreoRegistroLocalGateway.
 */
@Component
@ConditionalOnProperty(name = "CORREO_MODO", havingValue = "smtp")
public class CorreoSmtpGateway implements CorreoGateway {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoSmtpGateway.class);

    private final JavaMailSender mailSender;

    @Value("${CORREO_REMITENTE}")
    private String remitente;

    @Value("${CORREO_NOMBRE_REMITENTE:Municipalidad Distrital de San Miguel}")
    private String nombreRemitente;

    public CorreoSmtpGateway(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void enviarCorreo(String destino, String asunto, String cuerpo) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText(cuerpo, false);
            mailSender.send(mensaje);
            LOG.info("Correo enviado por SMTP: destino='{}', asunto='{}'", destino, asunto);
        } catch (Exception ex) {
            LOG.error("Error al enviar correo SMTP a '{}': {}", destino, ex.getMessage(), ex);
            throw new RuntimeException("Error al enviar correo a " + destino, ex);
        }
    }
}
