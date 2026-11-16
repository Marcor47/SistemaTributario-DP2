package pe.municipalidad.tributario.integracion.correo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/*
 * Registra el envío sin enviarlo: modo fallback cuando CORREO_MODO != smtp.
 * Se activa automáticamente si no hay ningún otro CorreoGateway en el contexto (p. ej. CorreoSmtpGateway).
 * Útil para desarrollo local y tests sin servidor de correo real.
 */
@Component
@ConditionalOnExpression("'${CORREO_MODO:local}' != 'smtp'")
public class CorreoRegistroLocalGateway implements CorreoGateway {

    @Override
    public boolean registraSinEnviar() { return true; }

    private static final Logger LOG = LoggerFactory.getLogger(CorreoRegistroLocalGateway.class);

    public record CorreoEnviado(String destino, String asunto, String cuerpo) {
    }

    private final List<CorreoEnviado> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviarCorreo(String destino, String asunto, String cuerpo) {
        enviados.add(new CorreoEnviado(destino, asunto, cuerpo));
        LOG.info("Correo registrado sin servidor de correo: asunto '{}'", asunto);
    }

    public List<CorreoEnviado> getEnviados() {
        return List.copyOf(enviados);
    }
}
