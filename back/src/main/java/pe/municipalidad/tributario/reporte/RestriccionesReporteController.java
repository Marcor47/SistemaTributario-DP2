package pe.municipalidad.tributario.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/** HU045: configuración de restricciones del catálogo por administrador. */
@RestController
@RequestMapping("/api/admin/restricciones-reportes")
@Transactional(readOnly = true)
public class RestriccionesReporteController {
    public record Peticion(@NotNull NombreRol rol, @NotNull TipoReporte tipoReporte,
                           @NotNull DimensionRestringida dimension, @Size(max = 80) String valor) {}
    public record Respuesta(Long id, NombreRol rol, TipoReporte tipoReporte,
                            DimensionRestringida dimension, String valor, boolean permitido) {
        static Respuesta desde(RestriccionReporte r) {
            return new Respuesta(r.getIdRestriccion(), r.getRol().getNombre(), r.getTipoReporte(),
                    r.getDimension(), r.getValorPermitido(), r.isPermitido());
        }
    }
    private final RestriccionReporteRepository restricciones;
    private final RolRepository roles;
    private final SesionInterna sesion;
    private final SamnetGateway samnet;
    public record Opciones(List<String> zonas, List<NombreRol> roles, List<TipoReporte> reportes,
                           List<DimensionRestringida> dimensiones) {}
    public RestriccionesReporteController(RestriccionReporteRepository restricciones, RolRepository roles,
                                          SesionInterna sesion, SamnetGateway samnet) {
        this.restricciones = restricciones; this.roles = roles; this.sesion = sesion;
        this.samnet = samnet;
    }

    @GetMapping
    public List<Respuesta> listar(HttpServletRequest request) {
        sesion.administrador(request);
        return restricciones.findAll().stream().map(Respuesta::desde).toList();
    }

    @GetMapping("/opciones")
    public Opciones opciones(HttpServletRequest request) {
        sesion.administrador(request);
        return new Opciones(samnet.obtenerZonas(), roles.findAll().stream().map(r -> r.getNombre()).toList(),
                List.of(TipoReporte.values()), List.of(DimensionRestringida.REPORTE, DimensionRestringida.ZONA));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Respuesta crear(@Valid @RequestBody Peticion peticion, HttpServletRequest request) {
        sesion.administrador(request);
        if (peticion.dimension() == DimensionRestringida.SEGMENTO) {
            throw new DatoInvalidoException("DIMENSION_NO_DISPONIBLE", "El filtro por segmento aún no está integrado.");
        }
        String valor = peticion.valor() == null ? null : peticion.valor().strip();
        if (peticion.dimension() == DimensionRestringida.ZONA && (valor == null || valor.isBlank())) {
            throw new DatoInvalidoException("ZONA_REQUERIDA", "Indique la zona que se restringirá.");
        }
        if (peticion.dimension() == DimensionRestringida.REPORTE && valor != null && !valor.isBlank()) {
            throw new DatoInvalidoException("VALOR_NO_APLICABLE", "Una restricción de reporte no lleva zona.");
        }
        if (peticion.dimension() == DimensionRestringida.ZONA) {
            String solicitada = valor;
            valor = samnet.obtenerZonas().stream().filter(z -> z.equalsIgnoreCase(solicitada)).findFirst()
                    .orElseThrow(() -> new DatoInvalidoException("ZONA_NO_ENCONTRADA",
                            "La zona indicada no existe. Seleccione una zona del catálogo."));
        }
        var rol = roles.findByNombre(peticion.rol()).orElseThrow(() ->
                new RecursoNoEncontradoException("ROL_NO_ENCONTRADO", "No existe el rol indicado."));
        String zona = valor;
        boolean existe = restricciones.findByRolAndTipoReporte(rol, peticion.tipoReporte()).stream()
                .anyMatch(r -> r.getDimension() == peticion.dimension() && !r.isPermitido()
                        && (r.getDimension() == DimensionRestringida.REPORTE
                            || r.getValorPermitido().equalsIgnoreCase(zona)));
        if (existe) throw new DatoInvalidoException("RESTRICCION_EXISTENTE", "La restricción ya existe.");
        return Respuesta.desde(restricciones.saveAndFlush(new RestriccionReporte(rol, peticion.tipoReporte(),
                peticion.dimension(), zona, false)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long id, HttpServletRequest request) {
        sesion.administrador(request);
        var restriccion = restricciones.findById(id).orElseThrow(() ->
                new RecursoNoEncontradoException("RESTRICCION_NO_ENCONTRADA", "No existe la restricción."));
        restricciones.delete(restriccion);
    }
}
