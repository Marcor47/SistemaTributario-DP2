package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

public class ConflictoException extends NegocioException {
    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.CONFLICT;
    }
}
