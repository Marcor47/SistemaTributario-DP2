package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class UsuarioAdminController {
    private final UsuarioAdminService servicio;
    private final SesionInterna sesion;

    public UsuarioAdminController(UsuarioAdminService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody CrearUsuarioSolicitud solicitud,
            HttpServletRequest request) {
        String admin = sesion.administrador(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearUsuario(solicitud, admin,
                request.getRemoteAddr()));
    }

    @PutMapping("/usuarios/{idUsuario}/rol")
    public UsuarioRespuesta cambiar(@PathVariable Long idUsuario, @Valid @RequestBody CambiarRolSolicitud solicitud,
            HttpServletRequest request) {
        return servicio.cambiarRol(idUsuario, solicitud, sesion.administrador(request), request.getRemoteAddr());
    }

    @GetMapping("/usuarios")
    public List<UsuarioRespuesta> usuarios(HttpServletRequest request) {
        sesion.administrador(request);
        return servicio.listarUsuarios();
    }

    @GetMapping("/roles")
    public List<RolRespuesta> roles(HttpServletRequest request) {
        sesion.administrador(request);
        return servicio.listarRoles();
    }
}
