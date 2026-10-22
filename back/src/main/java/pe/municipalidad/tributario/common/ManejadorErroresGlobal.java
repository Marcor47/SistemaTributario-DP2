package pe.municipalidad.tributario.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/* Traduce toda excepción al formato ErrorRespuesta para que el front tenga un solo contrato de error. */
@RestControllerAdvice
public class ManejadorErroresGlobal {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresGlobal.class);
    private static final String CODIGO_SOLICITUD_INVALIDA = "SOLICITUD_INVALIDA";
    private static final String MENSAJE_SOLICITUD_INVALIDA = "La solicitud tiene datos inválidos.";

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorRespuesta> manejarNegocio(NegocioException ex) {
        return ResponseEntity.status(ex.getEstadoHttp()).body(ErrorRespuesta.de(ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorRespuesta> manejarEstadoHttp(ResponseStatusException ex) {
        String mensaje = ex.getReason() == null ? "La solicitud no pudo completarse." : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(ErrorRespuesta.de("ERROR_HTTP", mensaje));
    }

    /* Errores de @Valid sobre el cuerpo JSON. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> manejarCuerpoInvalido(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return solicitudInvalida(detalles);
    }

    /* Errores de restricciones sobre parámetros (@RequestParam, @PathVariable). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorRespuesta> manejarParametroInvalido(HandlerMethodValidationException ex) {
        List<String> detalles = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> resultado.getMethodParameter().getParameterName() + ": "
                                + error.getDefaultMessage()))
                .sorted()
                .toList();
        return solicitudInvalida(detalles);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorRespuesta> manejarParametroFaltante(MissingServletRequestParameterException ex) {
        return solicitudInvalida(List.of(ex.getParameterName() + ": es obligatorio"));
    }

    /* Por ejemplo, un tipoDocumento que no existe o un id que no es numérico. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> manejarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return solicitudInvalida(List.of(ex.getName() + ": valor no permitido '" + ex.getValue() + "'"));
    }

    /* JSON mal formado o con un valor que no corresponde al tipo del campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> manejarJsonIlegible(HttpMessageNotReadableException ex) {
        return solicitudInvalida(List.of("El cuerpo de la solicitud no es un JSON válido para esta operación"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> manejarRutaInexistente(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorRespuesta.de("RUTA_NO_ENCONTRADA", "La ruta solicitada no existe."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorRespuesta.de("METODO_NO_PERMITIDO", "La operación no está permitida en esta ruta."));
    }

    /* Último recurso: se registra el detalle en el log y al cliente solo se le da un mensaje genérico. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> manejarInesperado(Exception ex) {
        LOG.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorRespuesta.de("ERROR_INTERNO", "Ocurrió un error inesperado. Intente nuevamente."));
    }

    private ResponseEntity<ErrorRespuesta> solicitudInvalida(List<String> detalles) {
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta(CODIGO_SOLICITUD_INVALIDA, MENSAJE_SOLICITUD_INVALIDA, detalles));
    }
}
