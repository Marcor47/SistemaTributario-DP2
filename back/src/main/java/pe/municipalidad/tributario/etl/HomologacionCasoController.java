package pe.municipalidad.tributario.etl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/*
 * RF-040: bandeja de homologación del Responsable de datos (revisión humana de los casos N2 del ETL). Solo API; la
 * pantalla queda pendiente. Mismos roles que la auditoría de decisiones (HU040, /api/homologacion/...).
 */
@RestController
@RequestMapping("/api/homologacion/casos")
public class HomologacionCasoController {

    private final HomologacionCasoService servicio;
    private final SesionInterna sesion;

    public HomologacionCasoController(HomologacionCasoService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    /* estado: PENDIENTE (por defecto), APROBADO, RECHAZADO, REASIGNADO o TODOS. */
    @GetMapping
    public List<CasoHomologacionRespuesta> listar(@RequestParam(required = false) String estado,
            HttpServletRequest request) {
        autorizar(request);
        return servicio.listar(estado(estado));
    }

    @GetMapping("/{idCaso}")
    public CasoHomologacionDetalleRespuesta detalle(@PathVariable Long idCaso, HttpServletRequest request) {
        return servicio.detalle(idCaso, autorizar(request));
    }

    @PostMapping("/{idCaso}/aprobacion")
    public ResolucionCasoRespuesta aprobar(@PathVariable Long idCaso,
            @Valid @RequestBody ResolverCasoPeticion peticion, HttpServletRequest request) {
        return servicio.aprobar(idCaso, peticion.motivo(), autorizar(request));
    }

    @PostMapping("/{idCaso}/rechazo")
    public ResolucionCasoRespuesta rechazar(@PathVariable Long idCaso,
            @Valid @RequestBody ResolverCasoPeticion peticion, HttpServletRequest request) {
        return servicio.rechazar(idCaso, peticion.motivo(), autorizar(request));
    }

    @PostMapping("/{idCaso}/reasignacion")
    public ResolucionCasoRespuesta reasignar(@PathVariable Long idCaso,
            @Valid @RequestBody ReasignarCasoPeticion peticion, HttpServletRequest request) {
        return servicio.reasignar(idCaso, peticion.idMaestro(), peticion.motivo(), autorizar(request));
    }

    private String autorizar(HttpServletRequest request) {
        return sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
    }

    private static EstadoCaso estado(String valor) {
        if (valor == null || valor.isBlank()) {
            return EstadoCaso.PENDIENTE;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if ("TODOS".equals(normalizado)) {
            return null;
        }
        try {
            return EstadoCaso.valueOf(normalizado);
        } catch (IllegalArgumentException ex) {
            throw new DatoInvalidoException("ESTADO_INVALIDO",
                    "Estado no válido: use PENDIENTE, APROBADO, RECHAZADO, REASIGNADO o TODOS.");
        }
    }
}
