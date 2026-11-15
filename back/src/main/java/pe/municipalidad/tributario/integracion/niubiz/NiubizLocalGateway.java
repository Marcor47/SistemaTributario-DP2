package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;

/* Pasarela local de contingencia con el mismo contrato de NiubizGateway (RNF-015): confirma el pago sin mover dinero. */
@Component
@ConditionalOnProperty(name = "tributario.niubiz.mode", havingValue = "local")
public class NiubizLocalGateway implements NiubizGateway {

    private final Map<String, BigDecimal> ordenes = new ConcurrentHashMap<>();

    @Override
    public OrdenPago crearOrdenPago(BigDecimal monto, String referencia) {
        String idOrden = "OPL-" + UUID.randomUUID();
        ordenes.put(idOrden, monto);
        return new OrdenPago(idOrden, "/pasarela/" + idOrden);
    }

    @Override
    public EstadoPagoPasarela consultarEstadoPago(String idOrden) {
        BigDecimal monto = idOrden == null ? null : ordenes.get(idOrden);
        if (monto == null) {
            throw new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA", "La pasarela no conoce la orden " + idOrden + ".");
        }
        return new EstadoPagoPasarela(idOrden, true, "AUT" + Math.abs(idOrden.hashCode() % 1_000_000), monto);
    }
}
