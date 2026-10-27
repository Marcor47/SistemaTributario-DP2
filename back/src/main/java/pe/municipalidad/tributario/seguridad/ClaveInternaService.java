package pe.municipalidad.tributario.seguridad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** pgcrypto usa bcrypt con sal aleatoria; la misma función verifica hashes almacenados. */
@Service
public class ClaveInternaService {
    private final JdbcTemplate jdbc;

    public ClaveInternaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String hashear(String clave) {
        return jdbc.queryForObject("SELECT public.crypt(?, public.gen_salt('bf', 12))", String.class, clave);
    }

    public boolean verificar(String clave, String hash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT public.crypt(?, ?) = ?", Boolean.class,
                clave, hash, hash));
    }
}
