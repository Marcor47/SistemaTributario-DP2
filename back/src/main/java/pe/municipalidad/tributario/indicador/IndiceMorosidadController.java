package pe.municipalidad.tributario.indicador;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU008: índice de morosidad total, por tributo y por zona. El ETL pide el cálculo en cada carga; el dashboard
 * consulta el último índice publicado.
 */
@RestController
@RequestMapping("/api/indicadores/morosidad")
public class IndiceMorosidadController {

    private final IndiceMorosidadService indiceMorosidadService;

    public IndiceMorosidadController(IndiceMorosidadService indiceMorosidadService) {
        this.indiceMorosidadService = indiceMorosidadService;
    }

    @GetMapping
    public IndiceMorosidadRespuesta obtener(
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio,
            @RequestParam(required = false) @Min(value = 1, message = "debe estar entre 1 y 12")
            @Max(value = 12, message = "debe estar entre 1 y 12") Integer numeroPeriodo) {
        return indiceMorosidadService.obtenerUltimo(anio, numeroPeriodo);
    }

    @PostMapping("/calculos")
    public CalculoMorosidadRespuesta calcular(@Valid @RequestBody LoteSolicitud solicitud) {
        return indiceMorosidadService.calcularParaLote(solicitud.idLote());
    }
}
