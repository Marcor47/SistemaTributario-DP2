package pe.municipalidad.tributario.constancia;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.auth.SesionContribuyente;

@RestController
@RequestMapping("/api/constancias")
@ConditionalOnProperty(name = "tributario.constancia.demo.habilitado", havingValue = "true")
public class ConstanciaDemoController {
    private final ConstanciaDemoService servicio;

    public ConstanciaDemoController(ConstanciaDemoService servicio) { this.servicio = servicio; }

    @PostMapping("/{id}/orden")
    public ConstanciaDemoService.OrdenRespuesta orden(@PathVariable Long id, HttpServletRequest request) {
        return servicio.crearOrden(SesionContribuyente.codigoContribuyente(request), id);
    }

    @GetMapping("/{id}/orden")
    public ConstanciaDemoService.OrdenRespuesta consultarOrden(@PathVariable Long id, HttpServletRequest request) {
        return servicio.consultarOrden(SesionContribuyente.codigoContribuyente(request), id);
    }

    @PostMapping("/{id}/pago")
    public ConstanciaDemoService.OrdenRespuesta pagar(@PathVariable Long id, HttpServletRequest request) {
        return servicio.confirmarPago(SesionContribuyente.codigoContribuyente(request), id);
    }

    @PostMapping("/{id}/emitir")
    public ConstanciaDemoService.DocumentoRespuesta emitir(@PathVariable Long id, HttpServletRequest request) {
        return servicio.emitir(SesionContribuyente.codigoContribuyente(request), id);
    }

    @GetMapping("/buzon")
    public List<ConstanciaDemoService.DocumentoRespuesta> buzon(HttpServletRequest request) {
        return servicio.listarBuzon(SesionContribuyente.codigoContribuyente(request));
    }

    @GetMapping("/buzon/{id}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, HttpServletRequest request) {
        byte[] pdf = servicio.descargar(SesionContribuyente.codigoContribuyente(request), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
