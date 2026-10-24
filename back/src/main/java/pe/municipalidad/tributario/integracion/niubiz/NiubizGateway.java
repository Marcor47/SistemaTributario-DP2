package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;

/*
 * Pasarela de pagos que ya usa la Municipalidad (RF-036, RNF-011). En la demostración solo se usa su
 * entorno de pruebas; mientras no haya credenciales, se usa NiubizSimuladoGateway (RNF-015).
 */
public interface NiubizGateway {

    OrdenPago crearOrdenPago(BigDecimal monto, String referencia);

    /* Consulta servidor a servidor; el retorno del navegador nunca confirma un pago (RNF-029). */
    EstadoPagoPasarela consultarEstadoPago(String idOrden);

    record OrdenPago(String idOrden, String urlPasarela) {
    }

    record EstadoPagoPasarela(String idOrden, boolean autorizado, String codigoAutorizacion) {
    }
}
