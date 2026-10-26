package pe.municipalidad.tributario.pagos;

import java.math.BigDecimal;
import java.util.Set;
import pe.municipalidad.tributario.constancia.TransaccionPago;

public record PagoRespuesta(Long idTransaccion, String estado, BigDecimal montoTotal, Set<String> obligaciones,
        String urlPasarela, String comprobante, boolean dePrueba) {

    public static PagoRespuesta desde(TransaccionPago transaccion, String urlPasarela) {
        return new PagoRespuesta(transaccion.getIdTransaccion(), transaccion.getEstado().name(),
                transaccion.getMontoTotal(), transaccion.getObligaciones(), urlPasarela,
                transaccion.getComprobante(), true);
    }
}
