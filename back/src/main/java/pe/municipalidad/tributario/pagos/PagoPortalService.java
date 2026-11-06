package pe.municipalidad.tributario.pagos;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.constancia.EstadoTransaccion;
import pe.municipalidad.tributario.constancia.DetallePago;
import pe.municipalidad.tributario.constancia.TransaccionPago;
import pe.municipalidad.tributario.constancia.TransaccionPagoRepository;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.gestiontributaria.BeneficioTributario;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
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
    private final JdbcTemplate jdbc;

    public PagoPortalService(TransaccionPagoRepository transacciones, ContribuyenteService contribuyentes,
            SamnetGateway samnet, NiubizGateway pasarela, Clock reloj, JdbcTemplate jdbc) {
        this.transacciones = transacciones;
        this.contribuyentes = contribuyentes;
        this.samnet = samnet;
        this.pasarela = pasarela;
        this.reloj = reloj;
        this.jdbc = jdbc;
    }

    @Transactional
    public PagoRespuesta iniciar(String codigo, PagoSolicitud solicitud, String clientIp) {
        List<DetallePago> detalles = cotizar(codigo);
        Set<Long> todos = detalles.stream().map(DetallePago::getIdObligacion).collect(Collectors.toSet());
        if (solicitud != null && solicitud.obligaciones() != null) {
            List<Long> seleccion = solicitud.obligaciones();
            if (seleccion.contains(null) || seleccion.size() != todos.size()
                    || !todos.equals(new HashSet<>(seleccion))) {
                throw new ReglaNegocioException("PAGO_DEBE_CUBRIR_TODO",
                        "El pago virtual debe cubrir todas las obligaciones pendientes. Consulte su cuenta.");
            }
        }
        var contribuyente = contribuyentes.obtenerPorCodigo(codigo);
        String documento = contribuyente.getNumeroDocumento();
        TransaccionPago transaccion = TransaccionPago.deObligaciones(documento, detalles, LocalDateTime.now(reloj));
        transacciones.saveAndFlush(transaccion);
        NiubizGateway.OrdenPago orden = pasarela.crearOrdenPago(transaccion.getMontoTotal(),
                "OBLIGACIONES-" + transaccion.getIdTransaccion(), contribuyente, clientIp);
        transaccion.iniciar(orden.idOrden());
        return PagoRespuesta.desde(transaccion, orden.urlPasarela(), orden.checkout());
    }

    @Transactional(readOnly = true)
    public PagoRespuesta consultar(String codigo, Long id) {
        return PagoRespuesta.desde(propia(codigo, id), null);
    }

    @Transactional
    public PagoRespuesta confirmar(String codigo, Long id, String transactionToken) {
        TransaccionPago transaccion = transacciones.bloquearPorId(id)
                .filter(t -> t.getDocumentoContribuyente().equals(documento(codigo)))
                .orElseThrow(() -> new RecursoNoEncontradoException("PAGO_NO_ENCONTRADO", "No existe ese pago."));
        if (transaccion.getEstado() == EstadoTransaccion.CONFIRMADA) {
            return PagoRespuesta.desde(transaccion, null);
        }
        if (transaccion.getEstado() != EstadoTransaccion.INICIADA) {
            throw new ReglaNegocioException("PAGO_CERRADO", "Este pago ya no puede confirmarse.");
        }
        // Serializa pagos del mismo contribuyente antes de autorizar externamente.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", ps -> ps.setString(1, codigo), rs -> {});
        List<DetallePago> actuales;
        try {
            actuales = cotizar(codigo);
        } catch (ReglaNegocioException ex) {
            if ("BENEFICIO_INDETERMINADO".equals(ex.getCodigo())) throw ex;
            throw saldoCambio();
        }
        Map<Long, DetallePago> original = transaccion.getDetalles().stream()
                .collect(Collectors.toMap(DetallePago::getIdObligacion, Function.identity()));
        if (original.isEmpty() || originalesCambiaron(original, actuales)) {
            throw saldoCambio();
        }
        NiubizGateway.EstadoPagoPasarela estado = pasarela.autorizar(transaccion.getIdOrdenPasarela(),
                transaccion.getMontoTotal(), transactionToken);
        if (!transaccion.getIdOrdenPasarela().equals(estado.idOrden()) || !estado.autorizado()
                || estado.montoAutorizado() == null
                || estado.montoAutorizado().compareTo(transaccion.getMontoTotal()) != 0) {
            throw new ReglaNegocioException("PAGO_NO_AUTORIZADO", "La pasarela aún no autorizó el pago.");
        }
        try {
            samnet.registrarPagoEnLinea(codigo, id, LocalDate.now(reloj));
        } catch (DataAccessException ex) {
            if (ex.getMostSpecificCause() instanceof SQLException sql && "P0001".equals(sql.getSQLState())) {
                throw saldoCambio();
            }
            throw ex;
        }
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

    private static ConflictoException saldoCambio() {
        return new ConflictoException("SALDO_CAMBIO",
                "La deuda o sus beneficios cambiaron desde que se creó la orden. Consulte su cuenta y cree un nuevo pago.");
    }

    private List<DetallePago> cotizar(String codigo) {
        EstadoCuenta cuenta = samnet.obtenerEstadoCuenta(codigo);
        if (cuenta.isDesdeCopia()) {
            throw ventanilla();
        }
        List<Obligacion> pendientes = cuenta.getObligaciones().stream()
                .filter(o -> o.saldo().signum() > 0).toList();
        if (pendientes.isEmpty()) {
            throw new ReglaNegocioException("SIN_DEUDA", "No hay obligaciones pendientes para crear un pago.");
        }
        List<BeneficioTributario> beneficios = samnet.verificarBeneficio(codigo);
        Map<Long, String> asignaciones = samnet.obtenerBeneficiosPorObligacion(codigo);
        List<DetallePago> detalles = new ArrayList<>();
        for (Obligacion obligacion : pendientes) {
            var porcentaje = obligacion.getPorcentajeDescuento();
            if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw ventanilla();
            }
            if (!asignaciones.containsKey(obligacion.getIdObligacion())) throw ventanilla();
            String idBeneficio = asignaciones.get(obligacion.getIdObligacion());
            if (porcentaje.signum() > 0) {
                List<BeneficioTributario> coincidentes = beneficios.stream()
                        .filter(b -> b.estaVigente(LocalDate.now(reloj)))
                        .filter(b -> codigo.equals(b.codigoContribuyente()))
                        .filter(b -> b.idBeneficio().equals(idBeneficio))
                        .filter(b -> b.porcentajeDescuento().compareTo(porcentaje) == 0).toList();
                if (idBeneficio == null || coincidentes.size() != 1) throw ventanilla();
            } else if (idBeneficio != null) {
                throw ventanilla();
            }
            detalles.add(new DetallePago(obligacion.getIdObligacion(), obligacion.saldo(),
                    obligacion.saldo().subtract(obligacion.montoConBeneficio()), idBeneficio));
        }
        if (!beneficios.isEmpty() && detalles.stream().noneMatch(d -> d.getIdBeneficio() != null)) {
            throw ventanilla();
        }
        var total = detalles.stream().map(d -> d.getMontoBase().subtract(d.getMontoDescuento()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            throw new ReglaNegocioException("SIN_COBRO_VIRTUAL",
                    "Los beneficios cubren toda la deuda. No se creó un cobro; acuda a ventanilla para su aplicación.");
        }
        return detalles;
    }

    private static boolean originalesCambiaron(Map<Long, DetallePago> original, List<DetallePago> actuales) {
        if (original.size() != actuales.size()) return true;
        for (DetallePago actual : actuales) {
            DetallePago anterior = original.get(actual.getIdObligacion());
            if (anterior == null || anterior.getMontoBase().compareTo(actual.getMontoBase()) != 0
                    || anterior.getMontoDescuento().compareTo(actual.getMontoDescuento()) != 0
                    || !Objects.equals(anterior.getIdBeneficio(), actual.getIdBeneficio())) return true;
        }
        return false;
    }

    private static ReglaNegocioException ventanilla() {
        return new ReglaNegocioException("BENEFICIO_INDETERMINADO",
                "No se pudo determinar el beneficio aplicable a cada obligación. Acuda a ventanilla.");
    }
}
