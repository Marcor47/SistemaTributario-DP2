package pe.municipalidad.tributario.seguridad;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Protege las contraseñas internas con BCrypt de coste 12. */
@Service
public class ClaveInternaService {
    private final PasswordEncoder encoder;

    public ClaveInternaService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String hashear(String clave) {
        return encoder.encode(clave);
    }

    public boolean verificar(String clave, String hash) {
        return encoder.matches(clave, hash);
    }
}
