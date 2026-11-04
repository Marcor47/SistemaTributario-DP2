package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU020 (RF-034): la solicitud de Constancia de No Adeudo la recibe un operador. Verifica la deuda en SAMNET; si es
 * 0 emite la orden de pago del derecho de trámite y, cuando el contribuyente la paga, emite la constancia y la envía
 * a su buzón. Ningún paso es automático: cada uno lo ejecuta el operador y queda registrado quién y cuándo.
 */
@Service
@Transactional(readOnly = true)
public class ConstanciaOperadorService {

    public static final String CODIGO_PLANTILLA = "CONSTANCIA_NO_ADEUDO";

    private static final Logger LOG = LoggerFactory.getLogger(ConstanciaOperadorService.class);
    private static final Set<EstadoConstancia> ESTADOS_CERRADOS =
            EnumSet.of(EstadoConstancia.EMITIDA, EstadoConstancia.RECHAZADA);
    private static final String PREDIOS_INCLUIDOS = "Todos los predios del contribuyente";
    private static final String RANGO_FECHAS = "Hasta la fecha de emisión";

    private final SolicitudConstanciaRepository solicitudConstanciaRepository;
    private final SolicitudConstanciaService solicitudConstanciaService;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ConstanciaRepository constanciaRepository;
    private final DocumentoBuzonRepository documentoBuzonRepository;
    private final VersionPlantillaRepository versionPlantillaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SamnetGateway samnet;
    private final FirmadorGateway firmador;
    private final GeneradorConstanciaPdf generadorPdf;
    private final Clock reloj;
    private final BigDecimal derechoTramite;

    public ConstanciaOperadorService(SolicitudConstanciaRepository solicitudConstanciaRepository,
            SolicitudConstanciaService solicitudConstanciaService, TransaccionPagoRepository transaccionPagoRepository,
            ConstanciaRepository constanciaRepository, DocumentoBuzonRepository documentoBuzonRepository,
            VersionPlantillaRepository versionPlantillaRepository, UsuarioRepository usuarioRepository,
            SamnetGateway samnet, FirmadorGateway firmador, GeneradorConstanciaPdf generadorPdf, Clock reloj,
            @Value("${tributario.constancia.derecho-tramite}") BigDecimal derechoTramite) {
        if (derechoTramite.signum() <= 0) {
            throw new IllegalArgumentException("El derecho de trámite de la constancia debe ser positivo.");
        }
        this.solicitudConstanciaRepository = solicitudConstanciaRepository;
        this.solicitudConstanciaService = solicitudConstanciaService;
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.constanciaRepository = constanciaRepository;
        this.documentoBuzonRepository = documentoBuzonRepository;
        this.versionPlantillaRepository = versionPlantillaRepository;
        this.usuarioRepository = usuarioRepository;
        this.samnet = samnet;
        this.firmador = firmador;
        this.generadorPdf = generadorPdf;
        this.reloj = reloj;
        this.derechoTramite = derechoTramite;
    }

    /*
     * Bandeja del operador, de la más antigua a la más reciente. Sin estado, trae las abiertas que ya le llegaron:
     * una solicitud de la app sin firmar todavía no se ha enviado (RF-021).
     */
    public List<SolicitudGestionRespuesta> listar(EstadoConstancia estado) {
        List<SolicitudConstancia> solicitudes = estado == null
                ? solicitudConstanciaRepository.buscarRecibidasAbiertas(ESTADOS_CERRADOS)
                : solicitudConstanciaRepository.findByEstadoOrderByFechaSolicitudAsc(estado);
        return solicitudes.stream().map(solicitud -> respuesta(solicitud, null)).toList();
    }

    /* El detalle consulta la deuda actual en SAMNET para que el operador decida con el dato vigente. */
    public SolicitudGestionRespuesta obtener(Long idSolicitud) {
        SolicitudConstancia solicitud = buscar(idSolicitud);
        DeudaConstanciaRespuesta deuda = ESTADOS_CERRADOS.contains(solicitud.getEstado())
                ? null
                : solicitudConstanciaService.consultarDeuda(codigoDe(solicitud));
        return respuesta(solicitud, deuda);
    }

    /*
     * Con deuda 0 la solicitud continúa y se emite la orden de pago del derecho de trámite (RF-036); con deuda, se
     * rechaza y la respuesta lleva el detalle de lo pendiente. Si SAMNET responde desde la copia, no se decide.
     */
    @Transactional
    public SolicitudGestionRespuesta verificarDeuda(Long idSolicitud, String nombreOperador) {
        SolicitudConstancia solicitud = buscarBloqueada(idSolicitud);
        Usuario operador = operador(nombreOperador);
        DeudaConstanciaRespuesta deuda = solicitudConstanciaService.consultarDeuda(codigoDe(solicitud));
        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (solicitud.verificarDeuda(operador, deuda.total(), ahora)) {
            TransaccionPago ordenPago = transaccionPagoRepository.save(
                    TransaccionPago.deDerechoTramite(solicitud, derechoTramite, ahora));
            solicitud.emitirOrdenPago(ordenPago);
        } else {
            LOG.info("Solicitud de constancia {} rechazada por deuda pendiente", idSolicitud);
        }
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), deuda);
    }

    /*
     * Tras el pago confirmado, el operador emite la constancia: se genera con la plantilla vigente, la firma la
     * Municipalidad (RF-037) y se deposita en el buzón del contribuyente (RF-030). Antes se vuelve a exigir deuda 0:
     * entre la verificación y la emisión pudo vencer otra cuota.
     */
    @Transactional
    public SolicitudGestionRespuesta emitir(Long idSolicitud, String nombreOperador) {
        SolicitudConstancia solicitud = buscarBloqueada(idSolicitud);
        if (solicitud.getEstado() != EstadoConstancia.PAGADA) {
            throw new ReglaNegocioException("PAGO_REQUERIDO",
                    "La solicitud está " + solicitud.getEstado() + ": la constancia se emite tras el pago del trámite.");
        }
        Usuario operador = operador(nombreOperador);
        Contribuyente contribuyente = contribuyenteDe(solicitud);
        DeudaConstanciaRespuesta deuda = solicitudConstanciaService.consultarDeuda(contribuyente.getCodigoContribuyente());
        if (!deuda.puedeSolicitar()) {
            throw new ReglaNegocioException("DEUDA_PENDIENTE",
                    "El contribuyente registra deuda pendiente de S/ " + deuda.total()
                            + ". No se puede emitir la constancia; rechace la solicitud.");
        }
        VersionPlantilla plantilla = versionPlantillaRepository.findByPlantillaCodigoAndVigenteTrue(CODIGO_PLANTILLA)
                .orElseThrow(() -> new ReglaNegocioException("PLANTILLA_NO_DISPONIBLE",
                        "No hay una versión vigente de la plantilla " + CODIGO_PLANTILLA + "."));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        String numero = constanciaRepository.siguienteNumero(ahora.toLocalDate());
        byte[] pdf = generadorPdf.generar(numero, contribuyente, ahora, firmador.esSimulado());
        FirmadorGateway.DocumentoFirmado firmado = firmador.firmarComoMunicipalidad(pdf);
        if (!firmado.firma().valida()) {
            throw new ReglaNegocioException("FIRMA_MUNICIPAL_INVALIDA",
                    "No se pudo firmar la constancia: " + firmado.firma().motivoRechazo() + ".");
        }

        solicitud.emitir(operador, new FirmaDigital(TipoFirmante.MUNICIPALIDAD,
                firmado.firma().entidadCertificadora(), ahora, true, firmado.firma().hashDocumento()), ahora);
        Constancia constancia = constanciaRepository.save(new Constancia(numero, solicitud, plantilla, ahora,
                PREDIOS_INCLUIDOS, RANGO_FECHAS, numero + ".pdf", firmador.esSimulado(), operador,
                firmado.contenido()));
        documentoBuzonRepository.save(
                DocumentoBuzon.deConstancia(constancia, solicitud.getDocumentoContribuyente(), ahora));
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), null);
    }

    @Transactional
    public SolicitudGestionRespuesta rechazar(Long idSolicitud, String motivo, String nombreOperador) {
        SolicitudConstancia solicitud = buscarBloqueada(idSolicitud);
        if (solicitud.getCanal() == CanalSolicitud.APP && solicitud.getEstado() == EstadoConstancia.INICIADA) {
            throw new ReglaNegocioException("SOLICITUD_NO_ENVIADA",
                    "El contribuyente aún no firma ni envía esta solicitud.");
        }
        solicitud.rechazar(operador(nombreOperador), motivo.strip(), LocalDateTime.now(reloj));
        transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(idSolicitud)
                .filter(orden -> orden.getEstado() == EstadoTransaccion.INICIADA)
                .ifPresent(TransaccionPago::rechazar);
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), null);
    }

    private SolicitudGestionRespuesta respuesta(SolicitudConstancia solicitud, DeudaConstanciaRespuesta deuda) {
        TransaccionPago ordenPago = transaccionPagoRepository
                .findBySolicitudConstancia_IdSolicitud(solicitud.getIdSolicitud()).orElse(null);
        String numeroConstancia = constanciaRepository.findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .map(Constancia::getNumeroConstancia).orElse(null);
        return SolicitudGestionRespuesta.desde(solicitud, ordenPago, numeroConstancia, deuda);
    }

    private SolicitudConstancia buscar(Long idSolicitud) {
        return solicitudConstanciaRepository.findById(idSolicitud).orElseThrow(() -> noEncontrada(idSolicitud));
    }

    private SolicitudConstancia buscarBloqueada(Long idSolicitud) {
        return solicitudConstanciaRepository.bloquearPorId(idSolicitud).orElseThrow(() -> noEncontrada(idSolicitud));
    }

    private static RecursoNoEncontradoException noEncontrada(Long idSolicitud) {
        return new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                "No existe la solicitud de constancia N.° " + idSolicitud + ".");
    }

    private String codigoDe(SolicitudConstancia solicitud) {
        return contribuyenteDe(solicitud).getCodigoContribuyente();
    }

    /* La solicitud guarda el DNI o RUC; SAMNET lo resuelve al contribuyente titular. */
    private Contribuyente contribuyenteDe(SolicitudConstancia solicitud) {
        String documento = solicitud.getDocumentoContribuyente();
        return samnet.buscarContribuyente(documento).stream()
                .filter(contribuyente -> documento.equals(contribuyente.getNumeroDocumento()))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "SAMNET no registra al contribuyente de la solicitud N.° " + solicitud.getIdSolicitud() + "."));
    }

    private Usuario operador(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new NoAutenticadoException("OPERADOR_NO_IDENTIFICADO",
                        "Inicie sesión con un usuario interno activo."));
    }
}
