package pe.municipalidad.tributario.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdministradorInicial implements ApplicationRunner {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final ClaveInternaService claves;
    private final Clock reloj;

    @Value("${tributario.seguridad.administrador-inicial.habilitado:false}") private boolean habilitado;
    @Value("${tributario.seguridad.administrador-inicial.usuario:}") private String usuario;
    @Value("${tributario.seguridad.administrador-inicial.nombre:}") private String nombre;
    @Value("${tributario.seguridad.administrador-inicial.correo:}") private String correo;
    @Value("${tributario.seguridad.administrador-inicial.clave:}") private String clave;

    public AdministradorInicial(UsuarioRepository usuarios, RolRepository roles, ClaveInternaService claves,
            Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.claves = claves;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!habilitado || usuarios.count() > 0) return;
        if (usuario.isBlank() || nombre.isBlank() || correo.isBlank() || !claveValida(clave)) {
            throw new IllegalStateException("Las variables del administrador inicial están incompletas.");
        }
        Rol administrador = roles.findByNombre(NombreRol.ADMINISTRADOR).orElseThrow();
        usuarios.save(new Usuario(usuario.trim().toLowerCase(Locale.ROOT), nombre.trim(),
                correo.trim().toLowerCase(Locale.ROOT), claves.hashear(clave), administrador,
                LocalDateTime.now(reloj), true));
    }

    private boolean claveValida(String valor) {
        return valor.length() >= 12 && valor.length() <= 64
                && valor.getBytes(StandardCharsets.UTF_8).length <= 72
                && valor.chars().anyMatch(Character::isUpperCase)
                && valor.chars().anyMatch(Character::isLowerCase)
                && valor.chars().anyMatch(Character::isDigit)
                && valor.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
