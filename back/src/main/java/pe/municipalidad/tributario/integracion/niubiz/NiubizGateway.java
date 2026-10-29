package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * Pasarela de pagos que ya usa la Municipalidad (RF-036, RNF-011). En la demostración solo se usa su
 * entorno de pruebas; mientras no haya credenciales, se usa NiubizSimuladoGateway (RNF-015).
 */
public interface NiubizGateway {

    OrdenPago crearOrdenPago(BigDecimal monto, String referencia);
    default OrdenPago crearOrdenPago(BigDecimal monto, String referencia, Contribuyente contribuyente, String clientIp) {
        return crearOrdenPago(monto, referencia);
    }

    /* Consulta servidor a servidor; el retorno del navegador nunca confirma un pago (RNF-029). */
    EstadoPagoPasarela consultarEstadoPago(String idOrden);
    default EstadoPagoPasarela autorizar(String idOrden, BigDecimal monto, String transactionToken) {
        return consultarEstadoPago(idOrden);
    }

    record OrdenPago(String idOrden, String urlPasarela, Checkout checkout) {
        public OrdenPago(String idOrden, String urlPasarela) { this(idOrden, urlPasarela, null); }
    }
    record Checkout(String sessionKey, String merchantId, String purchaseNumber, String checkoutUrl,
            String callbackUrl, String timeoutUrl) {}

    record EstadoPagoPasarela(String idOrden, boolean autorizado, String codigoAutorizacion,
            BigDecimal montoAutorizado) {
    }
}
