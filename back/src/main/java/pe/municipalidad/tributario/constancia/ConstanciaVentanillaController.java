package pe.municipalidad.tributario.constancia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU042: Módulo de Ventanilla para emisión presencial de la Constancia de No Adeudo (RF-055).
 * Permite al personal con rol VENTANILLA o permiso CONSTANCIAS:GESTIONAR:
 * - Consultar al contribuyente por DNI/RUC/código y evaluar su estado de deuda y predios.
 * - Emitir la constancia presencialmente reutilizando las reglas del portal, registrando al trabajador.
 * - Descargar o imprimir el documento PDF firmado con firma digital municipal.
 * - Listar las constancias emitidas.
 */
@RestController
@RequestMapping("/api/ventanilla/constancias")
@Validated
public class ConstanciaVentanillaController {

    private final ConstanciaVentanillaService constanciaVentanillaService;

    public ConstanciaVentanillaController(ConstanciaVentanillaService constanciaVentanillaService) {
        this.constanciaVentanillaService = constanciaVentanillaService;
    }

    /* Consulta previa por DNI o documento del contribuyente (RF-055, RF-034). */
    @GetMapping("/consulta")
    public ConsultaContribuyenteVentanillaRespuesta consultar(
            @RequestParam @NotBlank(message = "El documento o DNI es obligatorio") String documento) {
        return constanciaVentanillaService.consultar(documento);
    }

    /* Emisión de la Constancia de No Adeudo en Ventanilla (RF-055, RF-037, RF-030). */
    @PostMapping("/emitir")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstanciaVentanillaRespuesta emitir(
            @Valid @RequestBody EmitirConstanciaVentanillaSolicitud peticion,
            Principal trabajador) {
        return constanciaVentanillaService.emitir(peticion, trabajador.getName());
    }

    /* Descarga o impresión del PDF oficial con firma digital municipal (RF-055). */
    @GetMapping("/{numeroConstancia}/descarga")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String numeroConstancia) {
        byte[] pdf = constanciaVentanillaService.obtenerPdf(numeroConstancia);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(numeroConstancia + ".pdf")
                        .build().toString())
                .body(pdf);
    }

    /* Listado de constancias emitidas en ventanilla. */
    @GetMapping
    public List<ConstanciaVentanillaRespuesta> listar(
            @RequestParam(required = false) String documento) {
        return constanciaVentanillaService.listar(documento);
    }
}

