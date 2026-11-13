package pe.municipalidad.tributario.constancia;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;

/*
 * HU020 (RF-036): el contribuyente paga la orden del derecho de trámite que emitió el operador. El pago solo se da
 * por confirmado tras consultar a la pasarela servidor a servidor (RNF-029); con eso la solicitud queda PAGADA y, en
 * la misma operación, el sistema firma y emite la constancia (HU024, RF-037).
 */
@Service
@Transactional(readOnly = true)
public class OrdenPagoConstanciaService {

    private static final String PREFIJO_REFERENCIA = "CONSTANCIA-";
    private static final String PREFIJO_COMPROBANTE = "CONST-";

    private final SolicitudConstanciaRepository solicitudConstanciaRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ContribuyenteService contribuyenteService;
    private final ConstanciaRepository constanciaRepository;
    private final DocumentoBuzonRepository documentoBuzonRepository;
    private final NiubizGateway pasarela;
    private final FirmaAutomaticaConstanciaService firmaAutomatica;

    public OrdenPagoConstanciaService(SolicitudConstanciaRepository solicitudConstanciaRepository,
            TransaccionPagoRepository transaccionPagoRepository, ContribuyenteService contribuyenteService,
            ConstanciaRepository constanciaRepository, DocumentoBuzonRepository documentoBuzonRepository,
            NiubizGateway pasarela, FirmaAutomaticaConstanciaService firmaAutomatica) {
        this.solicitudConstanciaRepository = solicitudConstanciaRepository;
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.contribuyenteService = contribuyenteService;
        this.constanciaRepository = constanciaRepository;
        this.documentoBuzonRepository = documentoBuzonRepository;
        this.pasarela = pasarela;
        this.firmaAutomatica = firmaAutomatica;
    }

    public OrdenPagoConstanciaRespuesta obtener(String codigoContribuyente, Long idSolicitud) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud, false);
        return OrdenPagoConstanciaRespuesta.desde(ordenDe(solicitud), null, null);
    }

    /* No reemplaza una sesión previa: su resultado puede requerir conciliación antes de otro intento. */
    @Transactional
    public OrdenPagoConstanciaRespuesta iniciarPago(String codigoContribuyente, Long idSolicitud, String ipCliente) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud, true);
        TransaccionPago orden = ordenPendiente(solicitud);
        if (orden.getIdOrdenPasarela() != null) {
            throw new ReglaNegocioException("PAGO_YA_INICIADO",
                    "La orden ya tiene una sesión de pago. Consulte su estado antes de reintentar.");
        }
        Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(codigoContribuyente);
        NiubizGateway.OrdenPago ordenPasarela = pasarela.crearOrdenPago(orden.getMontoTotal(),
                PREFIJO_REFERENCIA + orden.getIdTransaccion(), contribuyente, ipCliente);
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
            return confirmada(orden, solicitud);
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
        Optional<EmisorConstancia.ConstanciaEmitida> emitida = firmaAutomatica.emitirTrasPago(solicitud);
        solicitudConstanciaRepository.saveAndFlush(solicitud);
        return emitida
                .map(e -> OrdenPagoConstanciaRespuesta.confirmada(orden, solicitud, e.constancia(),
                        e.documentoBuzon().getIdDocumento()))
                .orElseGet(() -> OrdenPagoConstanciaRespuesta.confirmada(orden, solicitud, null, null));
    }

    /* Repetir la confirmación devuelve el mismo resultado, con la constancia si ya se emitió. */
    private OrdenPagoConstanciaRespuesta confirmada(TransaccionPago orden, SolicitudConstancia solicitud) {
        Optional<Constancia> constancia = constanciaRepository.findBySolicitud_IdSolicitud(solicitud.getIdSolicitud());
        Long idDocumento = constancia.flatMap(c -> documentoBuzonRepository
                .findByConstancia_NumeroConstancia(c.getNumeroConstancia())).map(DocumentoBuzon::getIdDocumento)
                .orElse(null);
        return OrdenPagoConstanciaRespuesta.confirmada(orden, solicitud, constancia.orElse(null), idDocumento);
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
