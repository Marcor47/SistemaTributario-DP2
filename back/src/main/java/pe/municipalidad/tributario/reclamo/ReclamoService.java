package pe.municipalidad.tributario.reclamo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.auth.SesionContribuyente;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

@Service
public class ReclamoService {
    private final ReclamoRepository reclamos;
    private final UsuarioRepository usuarios;
    private final ContribuyenteService contribuyentes;
    private final AreaMunicipalRepository areas;
    private final DerivacionReclamoRepository derivaciones;
    private final DocumentoBuzonRepository documentosBuzon;
    private final RegistroAuditoriaRepository auditorias;
    private final Clock reloj;

    private static final Set<NombreRol> ROLES_RESPONSABLES = Set.of(NombreRol.GERENCIA,
            NombreRol.ANALISTA_RENTAS, NombreRol.RESPONSABLE_DATOS, NombreRol.VENTANILLA,
            NombreRol.TERMINALISTA);

    public ReclamoService(ReclamoRepository reclamos, UsuarioRepository usuarios,
            ContribuyenteService contribuyentes, AreaMunicipalRepository areas,
            DerivacionReclamoRepository derivaciones, DocumentoBuzonRepository documentosBuzon,
            RegistroAuditoriaRepository auditorias, Clock reloj) {
        this.reclamos = reclamos;
        this.usuarios = usuarios;
        this.contribuyentes = contribuyentes;
        this.areas = areas;
        this.derivaciones = derivaciones;
        this.documentosBuzon = documentosBuzon;
        this.auditorias = auditorias;
        this.reloj = reloj;
    }

    @Transactional
    public ReclamoRespuesta registrar(RegistrarReclamoSolicitud solicitud, HttpServletRequest request) {
        IdentidadReclamante identidad = identidad(request);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String numero = reclamos.siguienteNumero(ahora.toLocalDate());
        String referencia = normalizarOpcional(solicitud.referencia());
        String comentario = solicitud.comentario().trim();
        Reclamo reclamo = identidad.trabajador() == null
                ? Reclamo.deContribuyente(numero, identidad.documento(), solicitud.tipoReclamo(), referencia,
                        solicitud.fechaHoraIncidente(), comentario, ahora)
                : Reclamo.deTrabajador(numero, identidad.trabajador(), solicitud.tipoReclamo(), referencia,
                        solicitud.fechaHoraIncidente(), comentario, ahora);
        return ReclamoRespuesta.desde(reclamos.save(reclamo));
    }

    @Transactional(readOnly = true)
    public List<ReclamoRespuesta> listarPropios(HttpServletRequest request) {
        IdentidadReclamante identidad = identidad(request);
        List<Reclamo> propios = identidad.trabajador() == null
                ? reclamos.buscarDelContribuyente(identidad.documento())
                : reclamos.buscarDelTrabajador(identidad.trabajador());
        return propios.stream().map(ReclamoRespuesta::desde).toList();
    }

    @Transactional
    public ReclamoRespuesta cancelarPropio(Long idReclamo, HttpServletRequest request) {
        IdentidadReclamante identidad = identidad(request);
        Reclamo reclamo = detalle(idReclamo);
        if (!esPropio(reclamo, identidad)) {
            throw new RecursoNoEncontradoException("RECLAMO_NO_ENCONTRADO", "El reclamo no existe.");
        }
        reclamo.cancelar();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        auditorias.save(new RegistroAuditoria(identidad.trabajador(), "CANCELAR_RECLAMO", "Reclamo",
                "Abierto", reclamo.getNumeroReclamo(), ahora));
        return ReclamoRespuesta.desde(reclamo);
    }

    @Transactional(readOnly = true)
    public List<ReclamoGestionRespuesta> listarParaGestion(HttpServletRequest request) {
        exigirPermiso(request, "GESTIONAR");
        return reclamos.buscarTodosParaGestion().stream().map(ReclamoGestionRespuesta::desde).toList();
    }

    @Transactional(readOnly = true)
    public CatalogosReclamoRespuesta catalogos(HttpServletRequest request) {
        exigirPermiso(request, "GESTIONAR");
        List<AreaMunicipalRespuesta> areasActivas = areas.findByActivaTrueOrderByNombreAsc().stream()
                .map(AreaMunicipalRespuesta::desde).toList();
        List<ResponsableReclamoRespuesta> responsables = usuarios.buscarActivosConRoles().stream()
                .filter(usuario -> usuario.getRoles().stream().anyMatch(rol -> ROLES_RESPONSABLES.contains(rol.getNombre())))
                .map(ResponsableReclamoRespuesta::desde).toList();
        return new CatalogosReclamoRespuesta(areasActivas, responsables);
    }

    @Transactional
    public ReclamoGestionRespuesta derivar(Long idReclamo, DerivarReclamoSolicitud solicitud,
            HttpServletRequest request) {
        Usuario gestor = exigirPermiso(request, "GESTIONAR");
        Reclamo reclamo = detalle(idReclamo);
        AreaMunicipal area = areas.findById(solicitud.idArea()).filter(AreaMunicipal::isActiva).orElseThrow(() ->
                new RecursoNoEncontradoException("AREA_NO_ENCONTRADA", "El área seleccionada no existe o está inactiva."));
        Usuario responsable = usuarios.findConRolesByIdUsuario(solicitud.idResponsable()).filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("RESPONSABLE_NO_ENCONTRADO",
                        "El responsable seleccionado no existe o está inactivo."));
        if (responsable.getRoles().stream().noneMatch(rol -> ROLES_RESPONSABLES.contains(rol.getNombre()))) {
            throw new ReglaNegocioException("RESPONSABLE_NO_PERMITIDO",
                    "El usuario seleccionado no puede atender reclamos.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        DerivacionReclamo derivacion = reclamo.derivar(area, responsable, gestor,
                normalizarOpcional(solicitud.observacion()), ahora);
        derivaciones.save(derivacion);
        auditorias.save(new RegistroAuditoria(gestor, responsable, "DERIVAR_RECLAMO", "Reclamo",
                reclamo.getNumeroReclamo(), area.getNombre(), ahora));
        return ReclamoGestionRespuesta.desde(reclamo);
    }

    @Transactional(readOnly = true)
    public List<ReclamoGestionRespuesta> listarAsignados(HttpServletRequest request) {
        Usuario responsable = exigirPermiso(request, "ATENDER");
        return reclamos.buscarAsignadosA(responsable).stream().map(ReclamoGestionRespuesta::desde).toList();
    }

    @Transactional
    public ReclamoGestionRespuesta responder(Long idReclamo, ResponderReclamoSolicitud solicitud,
            HttpServletRequest request) {
        Usuario responsable = exigirPermiso(request, "ATENDER");
        Reclamo reclamo = detalle(idReclamo);
        if (reclamo.getResponsableActual() == null
                || !reclamo.getResponsableActual().getIdUsuario().equals(responsable.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el responsable actual puede responder este reclamo.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        reclamo.responder(solicitud.respuesta().trim(), ahora);
        if (reclamo.getTipoReclamante() == TipoReclamante.CONTRIBUYENTE) {
            documentosBuzon.save(DocumentoBuzon.respuestaAReclamo(reclamo, null, ahora));
        }
        auditorias.save(new RegistroAuditoria(responsable, "RESPONDER_RECLAMO", "Reclamo",
                "EN_REVISION", reclamo.getNumeroReclamo(), ahora));
        return ReclamoGestionRespuesta.desde(reclamo);
    }

    private IdentidadReclamante identidad(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object idInterno = sesion == null ? null : sesion.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        if (idInterno instanceof Long idUsuario) {
            Usuario usuario = usuarios.findConRolesByIdUsuario(idUsuario).orElseThrow(() ->
                    new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión para gestionar sus reclamos."));
            if (!usuario.isActivo()) {
                sesion.invalidate();
                throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión para gestionar sus reclamos.");
            }
            if (usuario.isCambioClavePendiente()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Debe cambiar su contraseña temporal antes de continuar.");
            }
            return new IdentidadReclamante(null, usuario);
        }
        String codigo = SesionContribuyente.codigoContribuyente(request);
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        return new IdentidadReclamante(documento, null);
    }

    private Usuario exigirPermiso(HttpServletRequest request, String accion) {
        HttpSession sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        if (!(id instanceof Long idUsuario)) {
            throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno.");
        }
        Usuario usuario = usuarios.findConRolesByIdUsuario(idUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
        if (!usuario.tienePermiso("RECLAMOS", accion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para esta operación.");
        }
        return usuario;
    }

    private Reclamo detalle(Long idReclamo) {
        return reclamos.buscarDetalle(idReclamo).orElseThrow(() ->
                new RecursoNoEncontradoException("RECLAMO_NO_ENCONTRADO", "El reclamo no existe."));
    }

    private boolean esPropio(Reclamo reclamo, IdentidadReclamante identidad) {
        if (identidad.trabajador() != null) {
            return reclamo.getUsuarioReclamante() != null
                    && reclamo.getUsuarioReclamante().getIdUsuario().equals(identidad.trabajador().getIdUsuario());
        }
        return identidad.documento().equals(reclamo.getDocumentoContribuyente());
    }

    private String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }

    private record IdentidadReclamante(String documento, Usuario trabajador) {
    }
}
