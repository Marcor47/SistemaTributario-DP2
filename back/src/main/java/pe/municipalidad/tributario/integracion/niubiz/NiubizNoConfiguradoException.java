package pe.municipalidad.tributario.integracion.niubiz;

import org.springframework.http.HttpStatus;
import pe.municipalidad.tributario.common.NegocioException;

/** Una configuración pendiente de pagos no debe impedir el acceso a los demás módulos. */
public class NiubizNoConfiguradoException extends NegocioException {
    public NiubizNoConfiguradoException() {
        super("NIUBIZ_NO_CONFIGURADO",
                "El pago con Niubiz sandbox aún no está habilitado. Contacte a la Municipalidad.");
    }

    @Override
    public HttpStatus getEstadoHttp() { return HttpStatus.SERVICE_UNAVAILABLE; }
}
