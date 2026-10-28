package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.auth.LoginLimiter;

@RestController
@RequestMapping("/api/auth/interno")
public class AuthInternaController {
    public record LoginInternoSolicitud(@NotBlank String nombreUsuario, @NotBlank String clave) {}

    private final UsuarioRepository usuarios;
    private final ClaveInternaService claves;
    private final LoginLimiter limiter;

    public AuthInternaController(UsuarioRepository usuarios, ClaveInternaService claves, LoginLimiter limiter) {
        this.usuarios = usuarios;
        this.claves = claves;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginInternoSolicitud solicitud, HttpServletRequest request) {
        if (solicitud.nombreUsuario().length() > 40 || solicitud.clave().length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credenciales inválidas.");
        }
        String ipKey = "interno:ip:" + request.getRemoteAddr();
        String userKey = "interno:usuario:" + solicitud.nombreUsuario();
        if (limiter.blocked(ipKey) || limiter.blocked(userKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Intenta más tarde.");
        }
        Usuario usuario = usuarios.findByNombreUsuario(solicitud.nombreUsuario()).orElse(null);
        if (usuario == null || !usuario.isActivo() || !claves.verificar(solicitud.clave(), usuario.getClaveHash())) {
            limiter.failed(ipKey);
            limiter.failed(userKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
        }
        limiter.succeeded(ipKey);
        limiter.succeeded(userKey);
        HttpSession anterior = request.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession sesion = request.getSession(true);
        sesion.setMaxInactiveInterval(900);
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
    }
}
