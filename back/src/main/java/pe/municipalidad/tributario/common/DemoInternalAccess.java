package pe.municipalidad.tributario.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("local")
public class DemoInternalAccess {
    private final String readKey;
    private final String adminKey;

    public DemoInternalAccess(@Value("${tributario.local.internal-read-key:}") String readKey,
            @Value("${tributario.local.internal-admin-key:}") String adminKey) {
        this.readKey = readKey;
        this.adminKey = adminKey;
    }

    public void requireRead(String supplied) {
        configured();
        if (!matches(readKey, supplied) && !matches(adminKey, supplied)) deny();
    }

    public void requireAdmin(String supplied) {
        configured();
        if (!matches(adminKey, supplied)) deny();
    }

    private void configured() {
        if (readKey.isBlank() || adminKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Acceso interno de prueba no configurado");
        }
    }

    private boolean matches(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void deny() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Acceso no autorizado");
    }
}
