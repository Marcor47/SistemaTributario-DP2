package pe.municipalidad.tributario.integracion.niubiz;

import org.springframework.http.HttpStatus;
import pe.municipalidad.tributario.common.NegocioException;

public class NiubizNoDisponibleException extends NegocioException {
    public NiubizNoDisponibleException(String mensaje, Throwable causa) {
        super("NIUBIZ_NO_DISPONIBLE", mensaje);
        initCause(causa);
    }

    @Override
    public HttpStatus getEstadoHttp() { return HttpStatus.BAD_GATEWAY; }
}
