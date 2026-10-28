package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.common.NoAutenticadoException;

/** Identidad interna tomada exclusivamente de una sesión autenticada. */
@Component
public class SesionInterna {
    public static final String ATRIBUTO_USUARIO_INTERNO = "internalUserId";
    private final UsuarioRepository usuarios;

    public SesionInterna(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
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

    /* Sin sesión interna: 401. Con sesión pero sin alguno de los roles indicados: 403. */
    @Transactional(readOnly = true)
    public String conRol(HttpServletRequest request, NombreRol... permitidos) {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(ATRIBUTO_USUARIO_INTERNO);
        if (!(id instanceof Long idUsuario)) {
            throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno.");
        }
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
        List<NombreRol> aceptados = List.of(permitidos);
        if (!usuario.isActivo() || usuario.getRoles().stream().noneMatch(rol -> aceptados.contains(rol.getNombre()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para esta consulta.");
        }
        return usuario.getNombreUsuario();
    }
}
