package pe.municipalidad.tributario.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(String codigoContribuyente, String password) {}
    /* El nombre se muestra en la barra superior del portal; es nulo si SAMNET no responde. */
    public record SessionResponse(String codigoContribuyente, String nombre) {}

    /* La sesión del contribuyente dura una hora sin actividad (6.5). */
    static final int DURACION_SESION_SEGUNDOS = 3600;

    private final MunicipalAuthClient municipal;
    private final LoginLimiter limiter;
    private final Optional<SamnetGateway> samnet;

    public AuthController(MunicipalAuthClient municipal, LoginLimiter limiter) {
        this(municipal, limiter, null);
    }

    @Autowired
    public AuthController(MunicipalAuthClient municipal, LoginLimiter limiter, SamnetGateway samnet) {
        this.municipal = municipal;
        this.limiter = limiter;
        this.samnet = Optional.ofNullable(samnet);
    }

    private SessionResponse respuesta(String codigo) {
        String nombre = null;
        try {
            nombre = samnet.flatMap(s -> s.buscarContribuyente(codigo).stream()
                    .filter(c -> codigo.equals(c.getCodigoContribuyente())).findFirst())
                    .map(Contribuyente::getNombreRazonSocial).orElse(null);
        } catch (RuntimeException ex) {
            nombre = null;
        }
        return new SessionResponse(codigo, nombre);
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest credentials, HttpServletRequest request) {
        if (credentials == null || credentials.codigoContribuyente() == null
                || credentials.password() == null || credentials.codigoContribuyente().isBlank()
                || credentials.codigoContribuyente().length() > 40 || credentials.password().isBlank()
                || credentials.password().length() > 256) {
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
        HttpSession existing = request.getSession(false);
        if (existing != null) existing.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(DURACION_SESION_SEGUNDOS);
        session.setAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE, subject);
        return respuesta(subject);
    }

    @GetMapping("/me")
    public SessionResponse me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return respuesta((String) session.getAttribute(SesionContribuyente.ATRIBUTO_CODIGO_CONTRIBUYENTE));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
