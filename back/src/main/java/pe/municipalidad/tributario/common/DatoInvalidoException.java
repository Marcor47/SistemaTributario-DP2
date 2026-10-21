package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/* Dato de entrada con formato incorrecto que solo puede validarse en el service (depende de otro dato). */
public class DatoInvalidoException extends NegocioException {

    public DatoInvalidoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.BAD_REQUEST;
    }
}
