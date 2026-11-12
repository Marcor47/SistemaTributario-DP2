package pe.municipalidad.tributario.documentos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    public BuzonRespuesta listar(HttpServletRequest request,
            @RequestParam(defaultValue = "RECIBIDOS") VistaBuzon vista,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) TipoDocumentoBuzon tipo,
            @RequestParam(required = false) Boolean leido,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {
        return buzonService.listar(SesionContribuyente.codigoContribuyente(request), vista, busqueda, tipo, leido,
                pagina, tamanio);
    }

    @GetMapping("/{idDocumento}")
    public DocumentoBuzonRespuesta abrir(@PathVariable Long idDocumento, HttpServletRequest request) {
        return buzonService.abrir(SesionContribuyente.codigoContribuyente(request), idDocumento);
    }

    @PostMapping("/{idDocumento}/no-leido")
    public DocumentoBuzonRespuesta marcarNoLeido(@PathVariable Long idDocumento, HttpServletRequest request) {
        return buzonService.marcarNoLeido(SesionContribuyente.codigoContribuyente(request), idDocumento);
    }

    @PatchMapping("/{idDocumento}/destacado")
    public DocumentoBuzonRespuesta cambiarDestacado(@PathVariable Long idDocumento,
            @Valid @RequestBody EstadoBuzonSolicitud solicitud, HttpServletRequest request) {
        return buzonService.cambiarDestacado(SesionContribuyente.codigoContribuyente(request), idDocumento,
                solicitud.valor());
    }

    @PatchMapping("/{idDocumento}/archivado")
    public DocumentoBuzonRespuesta cambiarArchivado(@PathVariable Long idDocumento,
            @Valid @RequestBody EstadoBuzonSolicitud solicitud, HttpServletRequest request) {
        return buzonService.cambiarArchivado(SesionContribuyente.codigoContribuyente(request), idDocumento,
                solicitud.valor());
    }

    @GetMapping("/{idDocumento}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable Long idDocumento, HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"documento-" + idDocumento + ".pdf\"")
                .body(buzonService.descargar(SesionContribuyente.codigoContribuyente(request), idDocumento));
    }
}

record EstadoBuzonSolicitud(@NotNull Boolean valor) {
}
