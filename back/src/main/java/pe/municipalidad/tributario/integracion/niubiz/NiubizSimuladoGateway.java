package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;

/* Simula la confirmación del pago con el mismo contrato de NiubizGateway (RNF-015); no mueve dinero. */
@Component
@ConditionalOnProperty(name = "tributario.niubiz.mode", havingValue = "simulado", matchIfMissing = true)
public class NiubizSimuladoGateway implements NiubizGateway {

    private final Map<String, BigDecimal> ordenes = new ConcurrentHashMap<>();

    @Override
    public OrdenPago crearOrdenPago(BigDecimal monto, String referencia) {
        String idOrden = "SIM-" + UUID.randomUUID();
        ordenes.put(idOrden, monto);
        return new OrdenPago(idOrden, "/pasarela-simulada/" + idOrden);
    }

    @Override
    public EstadoPagoPasarela consultarEstadoPago(String idOrden) {
        BigDecimal monto = idOrden == null ? null : ordenes.get(idOrden);
        if (monto == null) {
            throw new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA", "La pasarela no conoce la orden " + idOrden + ".");
        }
        return new EstadoPagoPasarela(idOrden, true, "SIM" + Math.abs(idOrden.hashCode() % 1_000_000), monto);
    }
}
