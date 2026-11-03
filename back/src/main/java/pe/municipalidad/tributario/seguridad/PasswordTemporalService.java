package pe.municipalidad.tributario.seguridad;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class PasswordTemporalService {
    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final SecureRandom random = new SecureRandom();

    public String generar() {
        StringBuilder clave = new StringBuilder("SM#");
        for (int i = 0; i < 13; i++) clave.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        return clave.toString();
    }
}
