package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/* La solicitud está bien formada pero una regla de negocio impide atenderla. */
public class ReglaNegocioException extends NegocioException {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
