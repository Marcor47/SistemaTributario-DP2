package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UsuarioInternoFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;

    public UsuarioInternoFilter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        if (id instanceof Long idUsuario) {
            Usuario usuario = usuarios.findConRolesByIdUsuario(idUsuario).orElse(null);
            if (usuario == null || !usuario.isActivo()) {
                sesion.invalidate();
            } else {
                Rol rol = usuario.getRoles().stream().findFirst().orElse(null);
                if (rol != null) {
                    List<SimpleGrantedAuthority> autoridades = usuario.isCambioClavePendiente()
                            ? List.of(new SimpleGrantedAuthority("CLAVE:CAMBIAR"))
                            : Arrays.stream(rol.getPermisos().split(",")).map(String::trim)
                                    .filter(valor -> !valor.isBlank()).map(SimpleGrantedAuthority::new).toList();
                    var autenticacion = new UsernamePasswordAuthenticationToken(usuario.getNombreUsuario(), null,
                            autoridades);
                    SecurityContextHolder.getContext().setAuthentication(autenticacion);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
