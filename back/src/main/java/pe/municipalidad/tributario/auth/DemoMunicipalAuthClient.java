package pe.municipalidad.tributario.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("demo")
public class DemoMunicipalAuthClient implements MunicipalAuthClient {
    private final String code;
    private final String password;

    public DemoMunicipalAuthClient(@Value("${tributario.demo.citizen-code:}") String code,
            @Value("${tributario.demo.citizen-password:}") String password) {
        this.code = code;
        this.password = password;
    }

    @Override
    public Optional<String> autenticar(String candidateCode, String candidatePassword) {
        if (code.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Credenciales de prueba no configuradas");
        }
        boolean codeMatches = MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),
                candidateCode.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                candidatePassword.getBytes(StandardCharsets.UTF_8));
        return codeMatches && passwordMatches ? Optional.of(code) : Optional.empty();
    }
}
