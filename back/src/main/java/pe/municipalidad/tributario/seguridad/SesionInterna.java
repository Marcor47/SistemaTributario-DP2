package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Identidad interna tomada exclusivamente de una sesión autenticada. */
@Component
public class SesionInterna {
    public static final String ATRIBUTO_USUARIO_INTERNO = "internalUserId";
    private final UsuarioRepository usuarios;

    public SesionInterna(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public String administrador(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(ATRIBUTO_USUARIO_INTERNO);
        if (!(id instanceof Long idUsuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere sesión de administrador.");
        }
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere sesión de administrador."));
        if (!usuario.isActivo() || usuario.getRoles().stream()
                .noneMatch(rol -> rol.getNombre() == NombreRol.ADMINISTRADOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere sesión de administrador.");
        }
        return usuario.getNombreUsuario();
    }
}
