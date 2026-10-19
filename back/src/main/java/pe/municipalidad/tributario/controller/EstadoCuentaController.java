package pe.municipalidad.tributario.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.municipalidad.tributario.dto.EstadoCuentaDTO;
import pe.municipalidad.tributario.service.EstadoCuentaService;

@RestController
@RequestMapping("/api/v1/contribuyentes")
public class EstadoCuentaController {

    private final EstadoCuentaService estadoCuentaService;

    public EstadoCuentaController(EstadoCuentaService estadoCuentaService) {
        this.estadoCuentaService = estadoCuentaService;
    }

    /**
     * HU004 (RF-004): Consultar estado de cuenta de un contribuyente
     * @param identificador Código de contribuyente (ej. C-100234) o Documento (DNI/RUC)
     * @return EstadoCuentaDTO con datos de contribuyente, resumen de deudas y detalle de obligaciones
     */
    @GetMapping("/{identificador}/estado-cuenta")
    public ResponseEntity<EstadoCuentaDTO> obtenerEstadoCuenta(@PathVariable("identificador") String identificador) {
        EstadoCuentaDTO estadoCuenta = estadoCuentaService.obtenerEstadoCuenta(identificador);
        return ResponseEntity.ok(estadoCuenta);
    }
}
