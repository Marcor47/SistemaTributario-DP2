package pe.municipalidad.tributario.documentos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.auth.SesionContribuyente;

/* HU019: buzón de documentos del contribuyente (RF-030). Contrato en back/HU019_API.md. */
@RestController
@RequestMapping("/api/buzon")
public class BuzonController {

    private final BuzonService buzonService;

    public BuzonController(BuzonService buzonService) {
        this.buzonService = buzonService;
    }

    @GetMapping
    public BuzonRespuesta listar(HttpServletRequest request) {
        return buzonService.listar(SesionContribuyente.codigoContribuyente(request));
    }

    @GetMapping("/{idDocumento}")
    public DocumentoBuzonRespuesta abrir(@PathVariable Long idDocumento, HttpServletRequest request) {
        return buzonService.abrir(SesionContribuyente.codigoContribuyente(request), idDocumento);
    }

    @GetMapping("/{idDocumento}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable Long idDocumento, HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"documento-" + idDocumento + ".pdf\"")
                .body(buzonService.descargar(SesionContribuyente.codigoContribuyente(request), idDocumento));
    }
}
