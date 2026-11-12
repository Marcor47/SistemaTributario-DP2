package pe.municipalidad.tributario.reporte;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes/solicitudes")
public class SolicitudReporteConsultaController {

    private final SolicitudReporteConsultaService servicio;

    public SolicitudReporteConsultaController(SolicitudReporteConsultaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public SolicitudesReporteRespuesta consultar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoReporte tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {
        return servicio.consultar(usuario, tipo, desde, hasta, pagina, tamanio);
    }
}
