package pe.municipalidad.tributario.pagos;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.constancia.EstadoTransaccion;
import pe.municipalidad.tributario.constancia.TransaccionPago;
import pe.municipalidad.tributario.constancia.TransaccionPagoRepository;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;

@Service
public class PagoPortalService {
    private final TransaccionPagoRepository transacciones;
    private final ContribuyenteService contribuyentes;
    private final SamnetGateway samnet;
    private final NiubizGateway pasarela;
    private final Clock reloj;

    public PagoPortalService(TransaccionPagoRepository transacciones, ContribuyenteService contribuyentes,
            SamnetGateway samnet, NiubizGateway pasarela, Clock reloj) {
        this.transacciones = transacciones;
        this.contribuyentes = contribuyentes;
        this.samnet = samnet;
        this.pasarela = pasarela;
        this.reloj = reloj;
    }

    @Transactional
    public PagoRespuesta iniciar(String codigo, PagoSolicitud solicitud) {
        List<Long> ids = solicitud.obligaciones();
        if (ids.size() != new HashSet<>(ids).size()) {
            throw new DatoInvalidoException("OBLIGACIONES_REPETIDAS", "No repita obligaciones en un pago.");
        }
        Map<Long, Obligacion> propias = samnet.obtenerEstadoCuenta(codigo).getObligaciones().stream()
                .collect(Collectors.toMap(Obligacion::getIdObligacion, Function.identity()));
        BigDecimal monto = BigDecimal.ZERO;
        for (Long id : ids) {
            Obligacion obligacion = propias.get(id);
            if (obligacion == null || obligacion.saldo().signum() <= 0) {
                throw new ReglaNegocioException("OBLIGACION_NO_PAGABLE",
                        "Una obligación no pertenece a su cuenta o ya está pagada.");
            }
            monto = monto.add(obligacion.saldo());
        }
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        TransaccionPago transaccion = TransaccionPago.deObligaciones(documento,
                ids.stream().map(String::valueOf).collect(Collectors.toSet()), monto, BigDecimal.ZERO,
                LocalDateTime.now(reloj));
        transacciones.saveAndFlush(transaccion);
        NiubizGateway.OrdenPago orden = pasarela.crearOrdenPago(monto, "OBLIGACIONES-" + transaccion.getIdTransaccion());
        transaccion.iniciar(orden.idOrden());
        return PagoRespuesta.desde(transaccion, orden.urlPasarela());
    }

    @Transactional(readOnly = true)
    public PagoRespuesta consultar(String codigo, Long id) {
        return PagoRespuesta.desde(propia(codigo, id), null);
    }

    @Transactional
    public PagoRespuesta confirmar(String codigo, Long id) {
        TransaccionPago transaccion = transacciones.bloquearPorId(id)
                .filter(t -> t.getDocumentoContribuyente().equals(documento(codigo)))
                .orElseThrow(() -> new RecursoNoEncontradoException("PAGO_NO_ENCONTRADO", "No existe ese pago."));
        if (transaccion.getEstado() == EstadoTransaccion.CONFIRMADA) {
            return PagoRespuesta.desde(transaccion, null);
        }
        if (transaccion.getEstado() != EstadoTransaccion.INICIADA) {
            throw new ReglaNegocioException("PAGO_CERRADO", "Este pago ya no puede confirmarse.");
        }
        NiubizGateway.EstadoPagoPasarela estado = pasarela.consultarEstadoPago(transaccion.getIdOrdenPasarela());
        if (!transaccion.getIdOrdenPasarela().equals(estado.idOrden()) || !estado.autorizado()) {
            throw new ReglaNegocioException("PAGO_NO_AUTORIZADO", "La pasarela aún no autorizó el pago.");
        }
        Set<Long> ids = transaccion.getObligaciones().stream().map(Long::valueOf).collect(Collectors.toSet());
        samnet.registrarPagoEnLinea(codigo, id, ids, transaccion.getMontoTotal(), LocalDate.now(reloj));
        transaccion.confirmar("WEB-" + id);
        return PagoRespuesta.desde(transaccion, null);
    }

    private TransaccionPago propia(String codigo, Long id) {
        return transacciones.findById(id)
                .filter(t -> t.getDocumentoContribuyente().equals(documento(codigo)))
                .orElseThrow(() -> new RecursoNoEncontradoException("PAGO_NO_ENCONTRADO", "No existe ese pago."));
    }

    private String documento(String codigo) {
        return contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
    }
}
