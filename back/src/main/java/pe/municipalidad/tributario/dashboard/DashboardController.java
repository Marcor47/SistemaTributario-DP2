package pe.municipalidad.tributario.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.obligacion.Tributo;

/* HU011: dashboard ejecutivo de Rentas, filtrable por periodo, zona, segmento y tributo. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardRespuesta consultar(
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Min(value = 1, message = "debe estar entre 1 y 12")
            @Max(value = 12, message = "debe estar entre 1 y 12") Integer numeroPeriodo,
            @RequestParam(required = false) @Size(max = 20, message = "admite hasta 20 caracteres") String zona,
            @RequestParam(required = false) Tributo tributo,
            @RequestParam(required = false) Long idSegmento) {
        return dashboardService.consultar(anio, numeroPeriodo, zona, tributo, idSegmento);
    }

    @GetMapping("/filtros")
    public DashboardFiltrosRespuesta filtros() {
        return dashboardService.filtros();
    }
}
