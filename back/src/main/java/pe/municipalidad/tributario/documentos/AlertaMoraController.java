package pe.municipalidad.tributario.documentos;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU012: Enviar alerta de mora temprana por correo (RF-013).
 * API para la Gerencia de Rentas y procesos del ETL.
 * Permite procesar lotes de cobranza publicados para detectar cuotas vencidas,
 * despachar los avisos por correo dentro de las 24 horas a los contribuyentes con correo registrado,
 * y consultar el historial de avisos por contribuyente y por lote.
 */
@RestController
@RequestMapping("/api/alertas-mora")
public class AlertaMoraController {

    private final AlertaMoraService alertaMoraService;

    public AlertaMoraController(AlertaMoraService alertaMoraService) {
        this.alertaMoraService = alertaMoraService;
    }

    @PostMapping("/evaluaciones")
    public RespuestaProcesamientoAlertasMora procesarAlertas(@Valid @RequestBody ProcesarAlertasMoraSolicitud solicitud) {
        return alertaMoraService.procesarAlertasLote(solicitud);
    }

    @GetMapping("/lotes/{idLote}")
    public List<AvisoCorreoRespuesta> listarPorLote(@PathVariable Long idLote) {
        return alertaMoraService.listarPorLote(idLote);
    }

    @GetMapping("/contribuyentes/{codigoODocumento}")
    public List<AvisoCorreoRespuesta> obtenerHistorialContribuyente(@PathVariable String codigoODocumento) {
        return alertaMoraService.obtenerHistorialContribuyente(codigoODocumento);
    }

    @GetMapping("/{idAviso}")
    public AvisoCorreoRespuesta obtenerPorId(@PathVariable Long idAviso) {
        return alertaMoraService.obtenerPorId(idAviso);
    }
}

