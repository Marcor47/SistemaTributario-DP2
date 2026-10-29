package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.auth.LoginLimiter;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;

@RestController
@RequestMapping("/api/auth/interno")
public class AuthInternaController {
    public record LoginInternoSolicitud(@NotBlank String nombreUsuario, @NotBlank String clave) {}

    private final UsuarioRepository usuarios;
    private final ClaveInternaService claves;
    private final LoginLimiter limiter;
    private final EventoSeguridadRepository eventos;
    private final Clock reloj;

    public AuthInternaController(UsuarioRepository usuarios, ClaveInternaService claves, LoginLimiter limiter,
            EventoSeguridadRepository eventos, Clock reloj) {
        this.usuarios = usuarios;
        this.claves = claves;
        this.limiter = limiter;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public SesionInternaRespuesta login(@Valid @RequestBody LoginInternoSolicitud solicitud,
            HttpServletRequest request) {
        if (solicitud.nombreUsuario().length() > 40
                || solicitud.clave().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credenciales inválidas.");
        }
        String nombre = solicitud.nombreUsuario().trim().toLowerCase(Locale.ROOT);
        String ipKey = "interno:ip:" + request.getRemoteAddr();
        String userKey = "interno:usuario:" + nombre;
        if (limiter.blocked(ipKey) || limiter.blocked(userKey)) {
            registrarEvento(TipoEventoSeguridad.LOGIN_FALLIDO, nombre, request, "Acceso bloqueado temporalmente");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Intenta más tarde.");
        }
        Usuario usuario = usuarios.findByNombreUsuarioIgnoreCase(nombre).orElse(null);
        if (usuario == null || !usuario.isActivo() || !claves.verificar(solicitud.clave(), usuario.getClaveHash())) {
            limiter.failed(ipKey);
            limiter.failed(userKey);
            registrarEvento(TipoEventoSeguridad.LOGIN_FALLIDO, nombre, request, "Credenciales inválidas");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
        }
        limiter.succeeded(ipKey);
        limiter.succeeded(userKey);
        HttpSession anterior = request.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession sesion = request.getSession(true);
        sesion.setMaxInactiveInterval(900);
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        registrarEvento(TipoEventoSeguridad.LOGIN_OK, usuario.getNombreUsuario(), request, "Inicio de sesión interno");
        return respuesta(usuario);
    }

    @GetMapping("/me")
    public SesionInternaRespuesta me(HttpServletRequest request) {
        return respuesta(usuarioSesion(request));
    }

    @PostMapping("/cambiar-clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void cambiarClave(@Valid @RequestBody CambiarClaveInternaSolicitud solicitud,
            HttpServletRequest request) {
        Usuario usuario = usuarioSesion(request);
        if (solicitud.claveActual().getBytes(StandardCharsets.UTF_8).length > 72
                || solicitud.claveNueva().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ReglaNegocioException("CLAVE_MUY_LARGA", "La contraseña no puede superar 72 bytes.");
        }
        if (!claves.verificar(solicitud.claveActual(), usuario.getClaveHash())) {
            throw new ReglaNegocioException("CLAVE_ACTUAL_INVALIDA", "La contraseña actual no es correcta.");
        }
        if (claves.verificar(solicitud.claveNueva(), usuario.getClaveHash())) {
            throw new ReglaNegocioException("CLAVE_REPETIDA", "La nueva contraseña debe ser diferente.");
        }
        if (!claveFuerte(solicitud.claveNueva())) {
            throw new ReglaNegocioException("CLAVE_DEBIL",
                    "La contraseña debe incluir mayúscula, minúscula, número y símbolo.");
        }
        usuario.cambiarClave(claves.hashear(solicitud.claveNueva()), LocalDateTime.now(reloj));
        usuarios.save(usuario);
        registrarEvento(TipoEventoSeguridad.CAMBIO_CLAVE, usuario.getNombreUsuario(), request,
                "Cambio de contraseña interno");
        HttpSession sesion = request.getSession(false);
        if (sesion != null) sesion.invalidate();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession actual = request.getSession(false);
        Object id = actual == null ? null : actual.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        if (id instanceof Long idUsuario) {
            usuarios.findById(idUsuario).ifPresent(usuario -> registrarEvento(TipoEventoSeguridad.LOGOUT,
                    usuario.getNombreUsuario(), request, "Cierre de sesión interno"));
        }
        HttpSession sesion = request.getSession(false);
        if (sesion != null) sesion.invalidate();
    }

    private Usuario usuarioSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        if (!(id instanceof Long idUsuario)) {
            throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno.");
        }
        Usuario usuario = usuarios.findConRolesByIdUsuario(idUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
        if (!usuario.isActivo()) {
            sesion.invalidate();
            throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno.");
        }
        return usuario;
    }

    private SesionInternaRespuesta respuesta(Usuario usuario) {
        Rol rol = usuario.getRoles().stream().findFirst().orElseThrow();
        Set<String> permisos = Arrays.stream(rol.getPermisos().split(","))
                .map(String::trim).filter(valor -> !valor.isBlank()).collect(Collectors.toUnmodifiableSet());
        return new SesionInternaRespuesta(usuario.getIdUsuario(), usuario.getNombreUsuario(),
                usuario.getNombreCompleto(), rol.getNombre(), permisos, usuario.isCambioClavePendiente());
    }

    private void registrarEvento(TipoEventoSeguridad tipo, String usuario, HttpServletRequest request,
            String detalle) {
        eventos.save(new EventoSeguridad(tipo, usuario.isBlank() ? "desconocido" : usuario,
                request.getRemoteAddr(), detalle, LocalDateTime.now(reloj)));
    }

    private boolean claveFuerte(String clave) {
        return clave.chars().anyMatch(Character::isUpperCase)
                && clave.chars().anyMatch(Character::isLowerCase)
                && clave.chars().anyMatch(Character::isDigit)
                && clave.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
