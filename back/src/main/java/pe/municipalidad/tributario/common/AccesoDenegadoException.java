package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/* El usuario está autenticado, pero una regla de acceso le impide ver el recurso. */
public class AccesoDenegadoException extends NegocioException {

    public AccesoDenegadoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.FORBIDDEN;
    }
}
