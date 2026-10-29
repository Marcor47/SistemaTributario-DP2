package pe.municipalidad.tributario.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.obligacion.Tributo;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/* HU006: catálogo de reportes de Seguimiento institucional para la Gerencia de Rentas. Contrato en back/HU006_API.md. */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final NombreRol[] ROLES_REPORTES =
            {NombreRol.GERENCIA, NombreRol.ANALISTA_RENTAS, NombreRol.ADMINISTRADOR};

    private final ReporteService servicio;
    private final SesionInterna sesion;

    public ReporteController(ReporteService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    public List<ReporteCatalogoRespuesta> catalogo(HttpServletRequest request) {
        return servicio.catalogo(sesion.conRol(request, ROLES_REPORTES));
    }

    @GetMapping("/{tipo}")
    public ReporteRespuesta<?> generar(
            @PathVariable TipoReporte tipo,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Size(max = 20, message = "es demasiado largo") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "no puede ser negativo")
            BigDecimal montoMinimo,
            HttpServletRequest request) {
        String usuario = sesion.conRol(request, ROLES_REPORTES);
        return servicio.generar(tipo, new FiltrosReporte(anio, zona, tributo, montoMinimo), usuario);
    }
}
