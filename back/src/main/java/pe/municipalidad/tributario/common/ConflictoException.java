package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/* El recurso ya existe o choca con el estado actual de los datos. */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.CONFLICT;
    }
}
