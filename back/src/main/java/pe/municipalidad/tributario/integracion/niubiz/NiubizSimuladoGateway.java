package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;

/* Simula la confirmación del pago con el mismo contrato de NiubizGateway (RNF-015); no mueve dinero. */
@Component
public class NiubizSimuladoGateway implements NiubizGateway {

    @Override
    public OrdenPago crearOrdenPago(BigDecimal monto, String referencia) {
        String idOrden = "SIM-" + UUID.randomUUID();
        return new OrdenPago(idOrden, "/pasarela-simulada/" + idOrden);
    }

    @Override
    public EstadoPagoPasarela consultarEstadoPago(String idOrden) {
        if (idOrden == null || !idOrden.matches("SIM-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA", "La pasarela no conoce la orden " + idOrden + ".");
        }
        return new EstadoPagoPasarela(idOrden, true, "SIM" + Math.abs(idOrden.hashCode() % 1_000_000));
    }
}
