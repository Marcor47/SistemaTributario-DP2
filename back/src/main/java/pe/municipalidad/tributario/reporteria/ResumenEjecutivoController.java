package pe.municipalidad.tributario.reporteria;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.ia.ResumenEjecutivo;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/* HU017 (RF-023): resumen ejecutivo del periodo con el modelo de lenguaje, revisión humana y descarga en PDF. */
@RestController
@RequestMapping("/api/resumenes-ejecutivos")
public class ResumenEjecutivoController {

    private static final NombreRol[] ROLES = {NombreRol.GERENCIA, NombreRol.ANALISTA_RENTAS};

    public record GenerarResumenPeticion(@NotBlank(message = "es obligatorio") String periodo) {
    }

    private final ResumenEjecutivoService servicio;
    private final GeneradorResumenEjecutivoPdf pdf;
    private final SesionInterna sesion;

    public ResumenEjecutivoController(ResumenEjecutivoService servicio, GeneradorResumenEjecutivoPdf pdf,
            SesionInterna sesion) {
        this.servicio = servicio;
        this.pdf = pdf;
        this.sesion = sesion;
    }

    @PostMapping
    public ResumenEjecutivoService.ResumenRespuesta generar(@Valid @RequestBody GenerarResumenPeticion peticion,
            HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return servicio.generar(peticion.periodo().strip());
    }

    @PostMapping("/{idResumen}/revision")
    public ResumenEjecutivoService.ResumenRespuesta revisar(@PathVariable Long idResumen,
            HttpServletRequest request) {
        return servicio.revisar(idResumen, sesion.conRol(request, ROLES));
    }

    @GetMapping("/{idResumen}/descarga")
    public Map<String, String> descargarTexto(@PathVariable Long idResumen, HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        return Map.of("texto", servicio.paraDescarga(idResumen).getTexto());
    }

    @GetMapping("/{idResumen}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long idResumen, HttpServletRequest request) {
        sesion.conRol(request, ROLES);
        ResumenEjecutivo resumen = servicio.paraDescarga(idResumen);
        byte[] contenido = pdf.generar(resumen, servicio.indicadoresDe(resumen));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resumen-ejecutivo-" + resumen.getPeriodo() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }
}
