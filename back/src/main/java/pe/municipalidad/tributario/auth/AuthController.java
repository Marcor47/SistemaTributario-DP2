package pe.municipalidad.tributario.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Profile("local")
public class AuthController {
    public record LoginRequest(String codigoContribuyente, String password) {}
    public record SessionResponse(String codigoContribuyente) {}
    public record CsrfResponse(String token, String headerName, String parameterName) {}

    private final MunicipalAuthClient municipal;
    private final LoginLimiter limiter;

    public AuthController(MunicipalAuthClient municipal, LoginLimiter limiter) {
        this.municipal = municipal;
        this.limiter = limiter;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(@RequestAttribute("_csrf") CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest credentials, HttpServletRequest request) {
        if (credentials == null || credentials.codigoContribuyente() == null
                || credentials.password() == null || credentials.codigoContribuyente().isBlank()
                || credentials.codigoContribuyente().length() > 40 || credentials.password().isBlank()
                || credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud inválida");
        }
        String userKey = "user:" + credentials.codigoContribuyente();
        String ipKey = "ip:" + request.getRemoteAddr();
        if (limiter.blocked(userKey) || limiter.blocked(ipKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Intenta más tarde");
        }
        String subject = municipal.autenticar(credentials.codigoContribuyente(), credentials.password())
                .orElse(null);
        if (subject == null) {
            limiter.failed(userKey);
            limiter.failed(ipKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        limiter.succeeded(userKey);
        limiter.succeeded(ipKey);
        HttpSession existing = request.getSession(false);
        if (existing != null) existing.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(900);
        session.setAttribute("citizenCode", subject);
        return new SessionResponse(subject);
    }

    @GetMapping("/me")
    public SessionResponse me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("citizenCode") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new SessionResponse((String) session.getAttribute("citizenCode"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
