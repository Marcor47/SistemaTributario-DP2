package pe.municipalidad.tributario.formularios;

import java.util.List;
import pe.municipalidad.tributario.common.DemoInternalAccess;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/formularios")
@Profile("demo")
public class FormularioController {
    private final FormularioService service;
    private final DemoInternalAccess access;

    public FormularioController(FormularioService service, DemoInternalAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<FormularioService.Resumen> listar(
            @RequestHeader(value = "X-Demo-Internal-Key", required = false) String key) {
        access.requireRead(key);
        return service.vigentes();
    }

    @GetMapping("/{codigo}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable String codigo,
            @RequestHeader(value = "X-Demo-Internal-Key", required = false) String key) {
        access.requireRead(key);
        VersionPlantilla f = service.vigente(codigo);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getTipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getArchivo()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.descargar());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FormularioService.Resumen> publicar(@RequestParam String codigo,
            @RequestParam String nombre, @RequestParam MultipartFile archivo,
            @RequestHeader(value = "X-Demo-Internal-Key", required = false) String key) {
        access.requireAdmin(key);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    service.publicar(codigo, nombre, archivo.getOriginalFilename(), archivo.getBytes()));
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
        }
    }
}
