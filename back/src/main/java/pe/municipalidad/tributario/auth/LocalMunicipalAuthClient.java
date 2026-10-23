package pe.municipalidad.tributario.auth;

import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalMunicipalAuthClient implements MunicipalAuthClient {
    private static final String HASH_INEXISTENTE =
            "$2a$12$v8AEdzob0w.KQ4pwcP3RL.exF4FDfi8Ikk6g8WFohMI7Sh.AS6ML.";

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public LocalMunicipalAuthClient(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<String> autenticar(String codigoContribuyente, String password) {
        String codigo = codigoContribuyente.trim().toUpperCase(Locale.ROOT);
        Optional<CredencialLocal> credencial = jdbc.sql("""
                SELECT codigo_contribuyente, password_hash, habilitada
                FROM seguridad.credencial_contribuyente_local
                WHERE codigo_contribuyente = :codigo
                """)
                .param("codigo", codigo)
                .query((resultado, fila) -> new CredencialLocal(resultado.getString("codigo_contribuyente"),
                        resultado.getString("password_hash"), resultado.getBoolean("habilitada")))
                .optional();
        String hash = credencial.map(CredencialLocal::passwordHash).orElse(HASH_INEXISTENTE);
        boolean passwordValido = passwordEncoder.matches(password, hash);
        return passwordValido && credencial.filter(CredencialLocal::habilitada).isPresent()
                ? Optional.of(credencial.orElseThrow().codigoContribuyente()) : Optional.empty();
    }

    private record CredencialLocal(String codigoContribuyente, String passwordHash, boolean habilitada) {}
}
