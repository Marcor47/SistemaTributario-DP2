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
 * HU042: módulo de Ventanilla para la emisión presencial de la Constancia de No Adeudo (RF-055) por pasos.
 * Permite al personal con permiso CONSTANCIAS:GESTIONAR:
 * - Consultar al contribuyente por DNI/RUC/código y evaluar su estado de deuda y predios.
 * - Crear la solicitud, recibir la firma capturada en el dispositivo presencial (RF-021) y verificar la deuda.
 * - Emitir la orden de pago del derecho de trámite, confirmar el cobro presencial y emitir la constancia.
 * - Descargar o imprimir el documento PDF firmado con firma digital municipal y listar las emitidas.
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

    /* Crear la solicitud en canal Ventanilla; si el contribuyente ya tiene una abierta, se retoma. */
    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudVentanillaRespuesta crearSolicitud(
            @Valid @RequestBody NuevaSolicitudVentanilla peticion, Principal trabajador) {
        return constanciaVentanillaService.crearSolicitud(peticion.documentoContribuyente(), trabajador.getName());
    }

    /* Documento de la solicitud que se muestra en el dispositivo de firma presencial (RF-021). */
    @GetMapping("/{idSolicitud}/documento-firma")
    public DocumentoFirmaRespuesta obtenerDocumentoFirma(@PathVariable Long idSolicitud) {
        return constanciaVentanillaService.obtenerDocumentoFirma(idSolicitud);
    }

    /* Registrar la firma capturada en el dispositivo presencial frente al contribuyente (RF-021). */
    @PostMapping("/{idSolicitud}/firma")
    public SolicitudVentanillaRespuesta registrarFirma(@PathVariable Long idSolicitud,
            @Valid @RequestBody FirmaSolicitudConstancia firma, Principal trabajador) {
        return constanciaVentanillaService.registrarFirma(idSolicitud, firma, trabajador.getName());
    }

    /* Verificación de deuda en SAMNET y, con deuda 0, emisión de la orden de pago (RF-034, RF-036). */
    @PostMapping("/{idSolicitud}/verificacion")
    public SolicitudVentanillaRespuesta verificarDeuda(@PathVariable Long idSolicitud, Principal trabajador) {
        return constanciaVentanillaService.verificarDeuda(idSolicitud, trabajador.getName());
    }

    /* Orden de pago del derecho de trámite asociada a la solicitud (RF-036). */
    @GetMapping("/{idSolicitud}/orden-pago")
    public OrdenPagoConstanciaRespuesta obtenerOrdenPago(@PathVariable Long idSolicitud) {
        return constanciaVentanillaService.obtenerOrdenPago(idSolicitud);
    }

    /* Confirmación del cobro presencial del derecho de trámite (RF-055). */
    @PostMapping("/{idSolicitud}/pago")
    public SolicitudVentanillaRespuesta confirmarPago(@PathVariable Long idSolicitud, Principal trabajador) {
        return constanciaVentanillaService.confirmarPago(idSolicitud, trabajador.getName());
    }

    /* Emisión de la constancia con los parámetros elegidos por el trabajador (RF-055, RF-037, RF-030). */
    @PostMapping("/{idSolicitud}/emitir")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstanciaVentanillaRespuesta emitir(@PathVariable Long idSolicitud,
            @Valid @RequestBody EmitirConstanciaVentanillaSolicitud peticion, Principal trabajador) {
        return constanciaVentanillaService.emitir(idSolicitud, peticion, trabajador.getName());
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
