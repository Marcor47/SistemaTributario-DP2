package pe.municipalidad.tributario.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/* HU037: reporte de Principales Contribuyentes (PRICOS, RF-050). Contrato en back/HU037_API.md. */
@RestController
@RequestMapping("/api/reportes/pricos")
public class ReportePricosController {

    private static final String LIMITE_POR_DEFECTO = "10";

    private final ReportePricosService servicio;
    private final SesionInterna sesion;

    public ReportePricosController(ReportePricosService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    public ReportePricosRespuesta generar(
            @RequestParam(defaultValue = "BASE_IMPONIBLE") CriterioPricos criterio,
            @RequestParam(defaultValue = LIMITE_POR_DEFECTO) @Min(value = 1, message = "debe estar entre 1 y 200")
            @Max(value = 200, message = "debe estar entre 1 y 200") int limite,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, NombreRol.GERENCIA, NombreRol.ANALISTA_RENTAS,
                NombreRol.ADMINISTRADOR);
        return servicio.generar(usuario, criterio, limite);
    }
}
