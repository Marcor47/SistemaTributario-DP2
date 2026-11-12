package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

/*
 * Orden de pago del derecho de trámite de la constancia (RF-036). urlPasarela y checkout solo vienen al iniciar el
 * pago en la pasarela; no se guardan. Al confirmar el pago, estadoSolicitud, numeroConstancia y urlDescarga dicen si
 * la constancia ya quedó firmada y lista para descargar (HU024); si la firma automática no pudo completarse, la
 * solicitud sigue PAGADA y los datos de la constancia van nulos.
 */
public record OrdenPagoConstanciaRespuesta(
        Long idTransaccion,
        BigDecimal monto,
        EstadoTransaccion estado,
        LocalDateTime fechaEmision,
        String comprobante,
        String urlPasarela,
        NiubizGateway.Checkout checkout,
        EstadoConstancia estadoSolicitud,
        String numeroConstancia,
        String urlDescarga) {

    /* Orden sin datos de la constancia: la usan los flujos que no emiten al pagar (p. ej. ventanilla, HU042). */
    public OrdenPagoConstanciaRespuesta(Long idTransaccion, BigDecimal monto, EstadoTransaccion estado,
            LocalDateTime fechaEmision, String comprobante, String urlPasarela, NiubizGateway.Checkout checkout) {
        this(idTransaccion, monto, estado, fechaEmision, comprobante, urlPasarela, checkout, null, null, null);
    }

    static OrdenPagoConstanciaRespuesta desde(TransaccionPago orden, String urlPasarela,
            NiubizGateway.Checkout checkout) {
        return new OrdenPagoConstanciaRespuesta(orden.getIdTransaccion(), orden.getMontoTotal(), orden.getEstado(),
                orden.getFechaHora(), orden.getComprobante(), urlPasarela, checkout);
    }

    static OrdenPagoConstanciaRespuesta confirmada(TransaccionPago orden, SolicitudConstancia solicitud,
            Constancia constancia, Long idDocumentoBuzon) {
        return new OrdenPagoConstanciaRespuesta(orden.getIdTransaccion(), orden.getMontoTotal(), orden.getEstado(),
                orden.getFechaHora(), orden.getComprobante(), null, null, solicitud.getEstado(),
                constancia == null ? null : constancia.getNumeroConstancia(),
                idDocumentoBuzon == null ? null : "/buzon/" + idDocumentoBuzon + "/descarga");
    }
}
