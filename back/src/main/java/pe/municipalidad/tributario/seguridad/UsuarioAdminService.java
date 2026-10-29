package pe.municipalidad.tributario.seguridad;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ConflictoException;

@Service
public class UsuarioAdminService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final RegistroAuditoriaRepository auditorias;
    private final EventoSeguridadRepository eventos;
    private final ClaveInternaService claves;
    private final PasswordTemporalService passwordsTemporales;
    private final Clock reloj;

    private static final Set<NombreRol> ROLES_ASIGNABLES = Set.of(NombreRol.ADMINISTRADOR, NombreRol.GERENCIA,
            NombreRol.ANALISTA_RENTAS, NombreRol.RESPONSABLE_DATOS, NombreRol.VENTANILLA,
            NombreRol.TERMINALISTA, NombreRol.GESTOR_RECLAMOS);

    public UsuarioAdminService(UsuarioRepository usuarios, RolRepository roles,
            RegistroAuditoriaRepository auditorias, EventoSeguridadRepository eventos,
            ClaveInternaService claves, PasswordTemporalService passwordsTemporales, Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.auditorias = auditorias;
        this.eventos = eventos;
        this.claves = claves;
        this.passwordsTemporales = passwordsTemporales;
        this.reloj = reloj;
    }

    @Transactional
    public UsuarioCreadoRespuesta crearUsuario(CrearUsuarioSolicitud solicitud, String adminUsuario,
            String ipOrigen) {
        Usuario admin = administrador(adminUsuario);
        String nombreUsuario = solicitud.nombreUsuario().trim().toLowerCase(Locale.ROOT);
        String correo = solicitud.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByNombreUsuarioIgnoreCase(nombreUsuario).isPresent()) {
            throw new ConflictoException("USUARIO_DUPLICADO", "El nombre de usuario ya existe.");
        }
        if (usuarios.existsByCorreoIgnoreCase(correo)) {
            throw new ConflictoException("CORREO_DUPLICADO", "El correo ya está registrado.");
        }
        Rol rol = buscarRol(solicitud.rol());
        String passwordTemporal = passwordsTemporales.generar();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Usuario usuario = usuarios.save(new Usuario(nombreUsuario, solicitud.nombreCompleto().trim(), correo,
                claves.hashear(passwordTemporal), rol, ahora, true));
        auditorias.save(new RegistroAuditoria(admin, usuario, "CREAR_USUARIO", "Usuario", null,
                rol.getNombre().name(), ahora));
        eventos.save(new EventoSeguridad(TipoEventoSeguridad.CREACION_USUARIO, adminUsuario, ipOrigen,
                "Creación de usuario " + usuario.getNombreUsuario() + " con rol " + rol.getNombre().name(), ahora));
        return new UsuarioCreadoRespuesta(respuesta(usuario), passwordTemporal);
    }

    @Transactional
    public UsuarioRespuesta cambiarRol(Long idUsuarioTarget, CambiarRolSolicitud solicitud,
            String adminUsuario, String ipOrigen) {
        Usuario admin = administrador(adminUsuario);
        Usuario usuario = usuarios.findById(idUsuarioTarget).orElseThrow(() ->
                new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO", "El usuario no existe."));
        String anterior = usuario.getRoles().stream().map(rol -> rol.getNombre().name()).sorted()
                .reduce((a, b) -> a + "," + b).orElse(null);
        if (usuario.getRoles().size() == 1 && solicitud.nuevoRol().name().equals(anterior)) {
            throw new ReglaNegocioException("ROL_IDENTICO", "El usuario ya tiene ese rol.");
        }
        Rol nuevo = buscarRol(solicitud.nuevoRol());
        usuario.sustituirRol(nuevo);
        usuarios.save(usuario);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        auditorias.save(new RegistroAuditoria(admin, usuario, "CAMBIO_ROL", "Usuario", anterior,
                nuevo.getNombre().name(), ahora));
        String detalle = "Cambio de rol de " + usuario.getNombreUsuario() + " de " + anterior + " a "
                + nuevo.getNombre().name();
        if (solicitud.motivo() != null && !solicitud.motivo().isBlank()) {
            detalle += " - Motivo: " + solicitud.motivo();
        }
        eventos.save(new EventoSeguridad(TipoEventoSeguridad.CAMBIO_ROL, adminUsuario, ipOrigen, detalle, ahora));
        return respuesta(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioRespuesta> listarUsuarios() {
        return usuarios.findAll().stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<RolRespuesta> listarRoles() {
        return roles.findAll().stream().filter(rol -> ROLES_ASIGNABLES.contains(rol.getNombre()))
                .map(rol -> new RolRespuesta(rol.getIdRol(), rol.getNombre(),
                rol.getDescripcion(), rol.getPermisos())).toList();
    }

    private Rol buscarRol(NombreRol nombre) {
        if (!ROLES_ASIGNABLES.contains(nombre)) {
            throw new ReglaNegocioException("ROL_NO_ASIGNABLE", "El rol no se puede asignar en esta operación.");
        }
        return roles.findByNombre(nombre).orElseThrow(() ->
                new RecursoNoEncontradoException("ROL_NO_ENCONTRADO", "El rol no existe."));
    }

    private Usuario administrador(String nombre) {
        Usuario admin = usuarios.findByNombreUsuario(nombre).orElseThrow(() ->
                new ReglaNegocioException("ADMIN_NO_AUTORIZADO", "Se requiere un administrador activo."));
        if (!admin.isActivo() || admin.getRoles().stream().noneMatch(r -> r.getNombre() == NombreRol.ADMINISTRADOR)) {
            throw new ReglaNegocioException("ADMIN_NO_AUTORIZADO", "Se requiere un administrador activo.");
        }
        return admin;
    }

    private UsuarioRespuesta respuesta(Usuario usuario) {
        NombreRol nombre = usuario.getRoles().stream().map(Rol::getNombre).findFirst().orElse(null);
        return new UsuarioRespuesta(usuario.getIdUsuario(), usuario.getNombreUsuario(), usuario.getNombreCompleto(),
                usuario.getCorreo(), nombre, usuario.isActivo(), usuario.isCambioClavePendiente(),
                usuario.getFechaCreacion());
    }
}
