package pe.municipalidad.tributario.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.Rol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/** HU045: configuración de restricciones del catálogo por administrador (denylist). */
@RestController
@RequestMapping("/api/admin/restricciones-reportes")
@Transactional(readOnly = true)
public class RestriccionesReporteController {

    public record Peticion(@NotNull NombreRol rol, @NotNull TipoReporte tipoReporte,
                           @NotNull DimensionRestringida dimension, @Size(max = 80) String valor) {}

    public record ReglaPeticion(@NotNull TipoReporte tipoReporte, @NotNull DimensionRestringida dimension,
                                @Size(max = 80) String valor) {}

    public record ConfiguracionPeticion(@NotNull @NotEmpty Map<NombreRol, List<ReglaPeticion>> roles) {}

    public record Respuesta(Long id, NombreRol rol, TipoReporte tipoReporte,
                            DimensionRestringida dimension, String valor, boolean permitido) {
        static Respuesta desde(RestriccionReporte r) {
            return new Respuesta(r.getIdRestriccion(), r.getRol().getNombre(), r.getTipoReporte(),
                    r.getDimension(), r.getValorPermitido(), r.isPermitido());
        }
    }

    public record Opciones(List<String> zonas, List<String> segmentos, List<NombreRol> roles,
                           List<TipoReporte> reportes, List<DimensionRestringida> dimensiones) {}

    private record ReglaValidada(Rol rol, TipoReporte tipoReporte, DimensionRestringida dimension, String valor) {}

    private final RestriccionReporteRepository restricciones;
    private final RolRepository roles;
    private final SesionInterna sesion;
    private final SamnetGateway samnet;
    private final SegmentoAccesoService segmentos;
    private final UsuarioRepository usuarios;
    private final RegistroAuditoriaRepository auditorias;
    private final Clock reloj;

    public RestriccionesReporteController(RestriccionReporteRepository restricciones, RolRepository roles,
                                          SesionInterna sesion, SamnetGateway samnet, SegmentoAccesoService segmentos,
                                          UsuarioRepository usuarios, RegistroAuditoriaRepository auditorias,
                                          Clock reloj) {
        this.restricciones = restricciones;
        this.roles = roles;
        this.sesion = sesion;
        this.samnet = samnet;
        this.segmentos = segmentos;
        this.usuarios = usuarios;
        this.auditorias = auditorias;
        this.reloj = reloj;
    }

    @GetMapping
    public List<Respuesta> listar(HttpServletRequest request) {
        sesion.conPermiso(request, "REPORTES", "CONFIGURAR");
        return restricciones.findAll().stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/opciones")
    public Opciones opciones(HttpServletRequest request) {
        sesion.conPermiso(request, "REPORTES", "CONFIGURAR");
        return new Opciones(samnet.obtenerZonas(), segmentos.publicados(),
                roles.findAll().stream().filter(rol -> rol.tienePermiso("REPORTES", "CONSULTAR"))
                        .map(Rol::getNombre).toList(),
                List.of(TipoReporte.values()),
                List.of(DimensionRestringida.REPORTE, DimensionRestringida.ZONA, DimensionRestringida.SEGMENTO));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Respuesta crear(@Valid @RequestBody Peticion peticion, HttpServletRequest request) {
        String usuario = sesion.conPermiso(request, "REPORTES", "CONFIGURAR");
        ReglaValidada regla = validar(peticion.rol(), peticion.tipoReporte(), peticion.dimension(),
                peticion.valor());
        if (existe(regla)) {
            throw new DatoInvalidoException("RESTRICCION_EXISTENTE", "La restricción ya existe.");
        }
        RestriccionReporte guardada = restricciones.saveAndFlush(new RestriccionReporte(regla.rol(),
                regla.tipoReporte(), regla.dimension(), regla.valor(), false));
        auditar(usuario, "CAMBIAR_RESTRICCION_REPORTE", null, reglaComoTexto(guardada));
        return Respuesta.desde(guardada);
    }

    @PutMapping
    @Transactional
    public List<Respuesta> reemplazar(@Valid @RequestBody ConfiguracionPeticion peticion,
                                      HttpServletRequest request) {
        String usuario = sesion.conPermiso(request, "REPORTES", "CONFIGURAR");
        Map<Rol, List<ReglaValidada>> nuevas = new LinkedHashMap<>();
        for (Map.Entry<NombreRol, List<ReglaPeticion>> entrada : peticion.roles().entrySet()) {
            Rol rol = roles.findByNombre(entrada.getKey()).orElseThrow(() ->
                    new RecursoNoEncontradoException("ROL_NO_ENCONTRADO", "No existe el rol indicado."));
            List<ReglaValidada> reglas = new ArrayList<>();
            if (entrada.getValue() != null) {
                for (ReglaPeticion regla : entrada.getValue()) {
                    if (regla == null || regla.tipoReporte() == null || regla.dimension() == null) {
                        throw new DatoInvalidoException("REGLA_INCOMPLETA",
                                "Cada restricción debe indicar reporte y dimensión.");
                    }
                    reglas.add(validar(entrada.getKey(), regla.tipoReporte(), regla.dimension(), regla.valor()));
                }
            }
            comprobarDuplicados(reglas);
            nuevas.put(rol, reglas);
        }

        StringBuilder anterior = new StringBuilder();
        for (Rol rol : nuevas.keySet()) {
            restricciones.findByRol(rol).forEach(regla -> anterior.append(reglaComoTexto(regla)).append('\n'));
            restricciones.deleteAll(restricciones.findByRol(rol));
        }
        restricciones.flush();

        List<Respuesta> respuesta = new ArrayList<>();
        StringBuilder nuevo = new StringBuilder();
        for (Map.Entry<Rol, List<ReglaValidada>> entrada : nuevas.entrySet()) {
            for (ReglaValidada regla : entrada.getValue()) {
                RestriccionReporte guardada = restricciones.save(new RestriccionReporte(entrada.getKey(),
                        regla.tipoReporte(), regla.dimension(), regla.valor(), false));
                respuesta.add(Respuesta.desde(guardada));
                nuevo.append(reglaComoTexto(guardada)).append('\n');
            }
        }
        restricciones.flush();
        auditar(usuario, "CAMBIAR_RESTRICCION_REPORTE", texto(anterior), texto(nuevo));
        return respuesta;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long id, HttpServletRequest request) {
        String usuario = sesion.conPermiso(request, "REPORTES", "CONFIGURAR");
        var restriccion = restricciones.findById(id).orElseThrow(() ->
                new RecursoNoEncontradoException("RESTRICCION_NO_ENCONTRADA", "No existe la restricción."));
        String anterior = reglaComoTexto(restriccion);
        restricciones.delete(restriccion);
        auditar(usuario, "CAMBIAR_RESTRICCION_REPORTE", anterior, null);
    }

    private ReglaValidada validar(NombreRol rolNombre, TipoReporte tipoReporte, DimensionRestringida dimension,
                                  String valorSolicitado) {
        if (rolNombre == NombreRol.ADMINISTRADOR) {
            throw new DatoInvalidoException("ROL_NO_RESTRINGIBLE",
                    "El rol Administrador no se puede restringir desde esta configuración.");
        }
        String valor = valorSolicitado == null ? null : valorSolicitado.strip();
        if (dimension == DimensionRestringida.ZONA) {
            if (valor == null || valor.isBlank()) {
                throw new DatoInvalidoException("ZONA_REQUERIDA", "Indique la zona que se restringirá.");
            }
            String buscada = valor;
            valor = samnet.obtenerZonas().stream().filter(zona -> zona.equalsIgnoreCase(buscada)).findFirst()
                    .orElseThrow(() -> new DatoInvalidoException("ZONA_NO_ENCONTRADA",
                            "La zona indicada no existe. Seleccione una zona del catálogo."));
        } else if (dimension == DimensionRestringida.SEGMENTO) {
            if (valor == null || valor.isBlank()) {
                throw new DatoInvalidoException("SEGMENTO_REQUERIDO", "Indique el segmento que se restringirá.");
            }
            String buscado = valor;
            valor = segmentos.publicados().stream().filter(segmento -> segmento.equalsIgnoreCase(buscado))
                    .findFirst().orElseThrow(() -> new DatoInvalidoException("SEGMENTO_NO_ENCONTRADO",
                            "El segmento indicado no está en la última segmentación publicada."));
        } else {
            if (valor != null && !valor.isBlank()) {
                throw new DatoInvalidoException("VALOR_NO_APLICABLE", "Una restricción de reporte no lleva zona.");
            }
            valor = null;
        }
        Rol rol = roles.findByNombre(rolNombre).orElseThrow(() ->
                new RecursoNoEncontradoException("ROL_NO_ENCONTRADO", "No existe el rol indicado."));
        return new ReglaValidada(rol, tipoReporte, dimension, valor);
    }

    private boolean existe(ReglaValidada regla) {
        return restricciones.findByRolAndTipoReporte(regla.rol(), regla.tipoReporte()).stream()
                .anyMatch(actual -> !actual.isPermitido() && actual.getDimension() == regla.dimension()
                        && (regla.dimension() == DimensionRestringida.REPORTE
                            || actual.getValorPermitido().equalsIgnoreCase(regla.valor())));
    }

    private static void comprobarDuplicados(List<ReglaValidada> reglas) {
        for (int i = 0; i < reglas.size(); i++) {
            for (int j = i + 1; j < reglas.size(); j++) {
                ReglaValidada a = reglas.get(i);
                ReglaValidada b = reglas.get(j);
                if (a.tipoReporte() == b.tipoReporte() && a.dimension() == b.dimension()
                        && (a.dimension() == DimensionRestringida.REPORTE
                            || a.valor().equalsIgnoreCase(b.valor()))) {
                    throw new DatoInvalidoException("RESTRICCION_DUPLICADA",
                            "La configuración contiene una restricción repetida.");
                }
            }
        }
    }

    private void auditar(String usuario, String accion, String anterior, String nuevo) {
        Usuario autor = usuarios.findByNombreUsuario(usuario).orElse(null);
        auditorias.save(new RegistroAuditoria(autor, accion, "RestriccionReporte", anterior, nuevo,
                LocalDateTime.now(reloj)));
    }

    private static String reglaComoTexto(RestriccionReporte regla) {
        return regla.getRol().getNombre() + "|" + regla.getTipoReporte() + "|" + regla.getDimension() + "|"
                + (regla.getValorPermitido() == null ? "" : regla.getValorPermitido());
    }

    private static String texto(StringBuilder valor) {
        return valor.isEmpty() ? null : valor.toString();
    }
}
