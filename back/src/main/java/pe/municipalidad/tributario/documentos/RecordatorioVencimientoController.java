package pe.municipalidad.tributario.documentos;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recordatorios-vencimiento")
public class RecordatorioVencimientoController {
    private final RecordatorioVencimientoService servicio;
    public RecordatorioVencimientoController(RecordatorioVencimientoService servicio) { this.servicio = servicio; }
    @PostMapping("/procesar")
    public RecordatorioVencimientoService.Resultado procesar() { return servicio.procesar(); }
}
