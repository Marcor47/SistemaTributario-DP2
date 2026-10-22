package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.NOT_FOUND;
    }
}
