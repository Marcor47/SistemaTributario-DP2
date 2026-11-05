package pe.municipalidad.tributario.reporteria;

import org.springframework.http.HttpStatus;
import pe.municipalidad.tributario.common.NegocioException;

/* El servicio de lenguaje no está configurado, no responde o rechazó la credencial. */
public class ModeloLenguajeNoDisponibleException extends NegocioException {

    public ModeloLenguajeNoDisponibleException(String mensaje) {
        super("IA_NO_DISPONIBLE", mensaje);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
