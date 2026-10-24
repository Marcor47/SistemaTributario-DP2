package pe.municipalidad.tributario.integracion.correo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/*
 * Registra el envío sin enviarlo (RNF-015). La implementación SMTP se agrega cuando se defina el servidor
 * de correo gratuito del despliegue.
 */
@Component
public class CorreoSimuladoGateway implements CorreoGateway {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoSimuladoGateway.class);

    public record CorreoEnviado(String destino, String asunto, String cuerpo) {
    }

    private final List<CorreoEnviado> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviarCorreo(String destino, String asunto, String cuerpo) {
        enviados.add(new CorreoEnviado(destino, asunto, cuerpo));
        LOG.info("Correo simulado: asunto '{}'", asunto);
    }

    public List<CorreoEnviado> getEnviados() {
        return List.copyOf(enviados);
    }
}
