package pe.municipalidad.tributario.pagos;

import java.math.BigDecimal;
import java.util.Set;
import pe.municipalidad.tributario.constancia.TransaccionPago;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

public record PagoRespuesta(Long idTransaccion, String estado, BigDecimal montoBase, BigDecimal montoDescuento,
        BigDecimal montoTotal, Set<String> obligaciones,
        String urlPasarela, String comprobante, boolean dePrueba, NiubizGateway.Checkout checkout) {

    public static PagoRespuesta desde(TransaccionPago transaccion, String urlPasarela) {
        return desde(transaccion, urlPasarela, null);
    }

    public static PagoRespuesta desde(TransaccionPago transaccion, String urlPasarela, NiubizGateway.Checkout checkout) {
        return new PagoRespuesta(transaccion.getIdTransaccion(), transaccion.getEstado().name(),
                transaccion.getMontoBase(), transaccion.getMontoDescuento(),
                transaccion.getMontoTotal(), transaccion.getObligaciones(), urlPasarela,
                transaccion.getComprobante(), true, checkout);
    }
}
