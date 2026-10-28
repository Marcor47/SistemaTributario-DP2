package pe.municipalidad.tributario.pagos;

import java.util.List;

/** La lista es opcional por compatibilidad; nunca determina el importe ni el alcance del pago. */
public record PagoSolicitud(List<Long> obligaciones) {
}
