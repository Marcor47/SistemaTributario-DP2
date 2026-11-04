package pe.municipalidad.tributario.constancia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

/*
 * HU020 (RF-036): el contribuyente paga la orden del derecho de trámite que emitió el operador. El pago solo se da
 * por confirmado tras consultar a la pasarela servidor a servidor (RNF-029); con eso la solicitud queda PAGADA y
 * vuelve al operador para emitir la constancia.
 */
@Service
@Transactional(readOnly = true)
public class OrdenPagoConstanciaService {

    private static final String PREFIJO_REFERENCIA = "CONSTANCIA-";
    private static final String PREFIJO_COMPROBANTE = "CONST-";

    private final SolicitudConstanciaRepository solicitudConstanciaRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ContribuyenteService contribuyenteService;
    private final NiubizGateway pasarela;

    public OrdenPagoConstanciaService(SolicitudConstanciaRepository solicitudConstanciaRepository,
            TransaccionPagoRepository transaccionPagoRepository, ContribuyenteService contribuyenteService,
            NiubizGateway pasarela) {
        this.solicitudConstanciaRepository = solicitudConstanciaRepository;
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.contribuyenteService = contribuyenteService;
        this.pasarela = pasarela;
    }

    public OrdenPagoConstanciaRespuesta obtener(String codigoContribuyente, Long idSolicitud) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud, false);
        return OrdenPagoConstanciaRespuesta.desde(ordenDe(solicitud), null, null);
    }

    /* Abre la sesión de pago en la pasarela; si la anterior expiró sin pagarse, se abre una nueva. */
    @Transactional
    public OrdenPagoConstanciaRespuesta iniciarPago(String codigoContribuyente, Long idSolicitud, String ipCliente) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud, true);
        TransaccionPago orden = ordenPendiente(solicitud);
        Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(codigoContribuyente);
        NiubizGateway.OrdenPago ordenPasarela = pasarela.crearOrdenPago(orden.getMontoTotal(),
                PREFIJO_REFERENCIA + idSolicitud, contribuyente, ipCliente);
        orden.iniciar(ordenPasarela.idOrden());
        transaccionPagoRepository.saveAndFlush(orden);
        return OrdenPagoConstanciaRespuesta.desde(orden, ordenPasarela.urlPasarela(), ordenPasarela.checkout());
    }

    @Transactional
    public OrdenPagoConstanciaRespuesta confirmarPago(String codigoContribuyente, Long idSolicitud,
            String transactionToken) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud, true);
        TransaccionPago orden = ordenDe(solicitud);
        if (orden.getEstado() == EstadoTransaccion.CONFIRMADA) {
            return OrdenPagoConstanciaRespuesta.desde(orden, null, null);
        }
        ordenPendiente(solicitud);
        if (orden.getIdOrdenPasarela() == null) {
            throw new ReglaNegocioException("PAGO_NO_INICIADO", "Primero inicie el pago en la pasarela.");
        }

        NiubizGateway.EstadoPagoPasarela estado = pasarela.autorizar(orden.getIdOrdenPasarela(),
                orden.getMontoTotal(), transactionToken);
        if (!estado.autorizado() || !orden.getIdOrdenPasarela().equals(estado.idOrden())
                || estado.montoAutorizado() == null
                || estado.montoAutorizado().compareTo(orden.getMontoTotal()) != 0) {
            throw new ReglaNegocioException("PAGO_NO_AUTORIZADO", "La pasarela aún no autoriza el pago.");
        }
        orden.confirmar(PREFIJO_COMPROBANTE + orden.getIdTransaccion());
        solicitud.confirmarPago(orden);
        solicitudConstanciaRepository.saveAndFlush(solicitud);
        return OrdenPagoConstanciaRespuesta.desde(orden, null, null);
    }

    private TransaccionPago ordenPendiente(SolicitudConstancia solicitud) {
        TransaccionPago orden = ordenDe(solicitud);
        if (solicitud.getEstado() != EstadoConstancia.ORDEN_PAGO_EMITIDA
                || orden.getEstado() != EstadoTransaccion.INICIADA) {
            throw new ReglaNegocioException("ORDEN_NO_PENDIENTE",
                    "La orden de pago de la solicitud N.° " + solicitud.getIdSolicitud() + " no está pendiente.");
        }
        return orden;
    }

    private TransaccionPago ordenDe(SolicitudConstancia solicitud) {
        return transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(solicitud.getIdSolicitud())
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA",
                        "El operador aún no emite la orden de pago de esta solicitud."));
    }

    /* Una solicitud de otro contribuyente se responde igual que una inexistente (RNF-023). */
    private SolicitudConstancia buscarPropia(String codigoContribuyente, Long idSolicitud, boolean bloquear) {
        String documento = contribuyenteService.obtenerPorCodigo(codigoContribuyente).getNumeroDocumento();
        return (bloquear ? solicitudConstanciaRepository.bloquearPorId(idSolicitud)
                        : solicitudConstanciaRepository.findById(idSolicitud))
                .filter(solicitud -> solicitud.getDocumentoContribuyente().equals(documento))
                .orElseThrow(() -> new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                        "No tiene una solicitud de constancia con N.° " + idSolicitud + "."));
    }
}
