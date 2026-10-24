package pe.municipalidad.tributario.integracion.samnet;

import org.springframework.http.HttpStatus;
import pe.municipalidad.tributario.common.NegocioException;

/* SAMNET o su capa de servicios no respondió; quien llama decide si usa la última copia (RNF-002). */
public class SamnetNoDisponibleException extends NegocioException {

    public SamnetNoDisponibleException(Throwable causa) {
        super("SAMNET_NO_DISPONIBLE", "El servicio de SAMNET no está disponible en este momento.");
        initCause(causa);
    }

    @Override
    public HttpStatus getEstadoHttp() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
