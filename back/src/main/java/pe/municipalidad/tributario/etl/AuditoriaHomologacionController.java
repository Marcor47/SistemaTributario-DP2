package pe.municipalidad.tributario.etl;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/*
 * HU040: auditar las decisiones de homologación (RF-053).
 * Como Responsable de datos, consultar el detalle de cada decisión de homologación:
 * nivel que la resolvió, puntaje, atributos que coincidieron y si fue automática o humana,
 * además del usuario, fecha y motivo cuando hubo intervención humana.
 * La auditoría es estrictamente inalterable (no permite modificación desde la aplicación).
 */
@RestController
@RequestMapping("/api/homologacion")
public class AuditoriaHomologacionController {

    private final AuditoriaHomologacionService servicio;
    private final AuditoriaAccesosService accesos;
    private final SesionInterna sesion;

    public AuditoriaHomologacionController(AuditoriaHomologacionService servicio, AuditoriaAccesosService accesos,
            SesionInterna sesion) {
        this.servicio = servicio;
        this.accesos = accesos;
        this.sesion = sesion;
    }

    /* Accesos a datos personales en claro (REIDENTIFICAR), lo más reciente primero. Solo lectura. */
    @GetMapping("/accesos")
    public List<AccesoDatoRespuesta> listarAccesos(
            @RequestParam(defaultValue = "200") int limite,
            HttpServletRequest request) {
        sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
        return accesos.recientes(limite);
    }

    @GetMapping("/maestros/{idMaestro}/decisiones")
    public List<DecisionHomologacionRespuesta> listarPorMaestro(
            @PathVariable Long idMaestro,
            HttpServletRequest request) {
        sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
        return servicio.listarPorMaestro(idMaestro);
    }

    @GetMapping("/maestros/seudonimo/{seudonimo}/decisiones")
    public List<DecisionHomologacionRespuesta> listarPorSeudonimo(
            @PathVariable String seudonimo,
            HttpServletRequest request) {
        sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
        return servicio.listarPorSeudonimo(seudonimo.trim());
    }

    /* HU040: búsqueda por el código de SAMNET del contribuyente o predio. Solo lectura. */
    @GetMapping("/decisiones")
    public List<DecisionHomologacionRespuesta> listarPorCodigo(
            @RequestParam TipoEntidad tipoEntidad,
            @RequestParam String codigo,
            HttpServletRequest request) {
        sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
        return servicio.listarPorCodigo(tipoEntidad, codigo.trim());
    }

    @GetMapping("/decisiones/{idDecision}")
    public DecisionHomologacionRespuesta obtenerDecision(
            @PathVariable Long idDecision,
            HttpServletRequest request) {
        sesion.conRol(request, NombreRol.RESPONSABLE_DATOS, NombreRol.ADMINISTRADOR);
        return servicio.obtenerDecision(idDecision);
    }
}

