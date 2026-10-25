package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/* La operación requiere una sesión iniciada y no la hay (o expiró). */
public class NoAutenticadoException extends NegocioException {

    public NoAutenticadoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.UNAUTHORIZED;
    }
}
