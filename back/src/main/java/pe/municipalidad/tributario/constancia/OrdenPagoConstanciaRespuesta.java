package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

/*
 * Orden de pago del derecho de trámite de la constancia (RF-036). urlPasarela y checkout solo vienen al iniciar el
 * pago en la pasarela; no se guardan.
 */
public record OrdenPagoConstanciaRespuesta(
        Long idTransaccion,
        BigDecimal monto,
        EstadoTransaccion estado,
        LocalDateTime fechaEmision,
        String comprobante,
        String urlPasarela,
        NiubizGateway.Checkout checkout) {

    static OrdenPagoConstanciaRespuesta desde(TransaccionPago orden, String urlPasarela,
            NiubizGateway.Checkout checkout) {
        return new OrdenPagoConstanciaRespuesta(orden.getIdTransaccion(), orden.getMontoTotal(), orden.getEstado(),
                orden.getFechaHora(), orden.getComprobante(), urlPasarela, checkout);
    }
}
