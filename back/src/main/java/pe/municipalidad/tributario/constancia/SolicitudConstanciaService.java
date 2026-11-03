package pe.municipalidad.tributario.constancia;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway.ResultadoFirma;

/*
 * HU015 (RF-021, RNF-007): el contribuyente firma digitalmente su solicitud de Constancia de No Adeudo desde la
 * app. Sin una firma válida la solicitud no se envía al operador, que luego verifica la deuda (RF-034).
 */
@Service
@Transactional(readOnly = true)
public class SolicitudConstanciaService {

    private static final Logger LOG = LoggerFactory.getLogger(SolicitudConstanciaService.class);
    private static final Set<EstadoConstancia> ESTADOS_CERRADOS =
            EnumSet.of(EstadoConstancia.EMITIDA, EstadoConstancia.RECHAZADA);
    private static final String TIPO_CONTENIDO_DOCUMENTO = "text/plain; charset=UTF-8";
    private static final String MARCA_DOCUMENTO_DE_PRUEBA = "DOCUMENTO DE PRUEBA";

    private final SolicitudConstanciaRepository solicitudConstanciaRepository;
    private final ContribuyenteService contribuyenteService;
    private final FirmadorGateway firmador;
    private final SamnetGateway samnet;
    private final Clock reloj;

    public SolicitudConstanciaService(SolicitudConstanciaRepository solicitudConstanciaRepository,
            ContribuyenteService contribuyenteService, FirmadorGateway firmador, SamnetGateway samnet, Clock reloj) {
        this.solicitudConstanciaRepository = solicitudConstanciaRepository;
        this.contribuyenteService = contribuyenteService;
        this.firmador = firmador;
        this.samnet = samnet;
        this.reloj = reloj;
    }

    @Transactional
    public SolicitudConstanciaRespuesta iniciarSolicitud(String codigoContribuyente) {
        exigirSinDeuda(codigoContribuyente);
        String documento = documentoDe(codigoContribuyente);
        /* El firmador simulado permite repetir la demostración después de firmar; la solicitud sin firmar se retoma. */
        var enCurso = firmador.esSimulado()
                ? solicitudConstanciaRepository.findFirstByDocumentoContribuyenteAndEstadoOrderByFechaSolicitudDesc(
                        documento, EstadoConstancia.INICIADA)
                : solicitudConstanciaRepository.findFirstByDocumentoContribuyenteAndEstadoNotInOrderByFechaSolicitudDesc(
                        documento, ESTADOS_CERRADOS);
        enCurso
                .ifPresent(solicitudExistente -> {
                    throw new ConflictoException("SOLICITUD_EN_CURSO",
                            "Ya tiene la solicitud de constancia N.° " + solicitudExistente.getIdSolicitud()
                                    + " en curso. Continúe con ella.");
                });

        /* Sin fracciones de segundo: la fecha forma parte del documento firmado y debe releerse igual de la BD. */
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.APP, documento, null, ahora);
        return SolicitudConstanciaRespuesta.desde(solicitudConstanciaRepository.save(solicitud));
    }

    public SolicitudConstanciaRespuesta obtenerSolicitud(String codigoContribuyente, Long idSolicitud) {
        return SolicitudConstanciaRespuesta.desde(buscarPropia(codigoContribuyente, idSolicitud));
    }

    public List<SolicitudConstanciaRespuesta> listarSolicitudes(String codigoContribuyente) {
        return solicitudConstanciaRepository
                .findByDocumentoContribuyenteOrderByFechaSolicitudDesc(documentoDe(codigoContribuyente))
                .stream().map(SolicitudConstanciaRespuesta::desde).toList();
    }

    public DocumentoFirmaRespuesta obtenerDocumentoParaFirma(String codigoContribuyente, Long idSolicitud) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud);
        exigirPendienteDeFirma(solicitud);
        exigirSinDeuda(codigoContribuyente);
        byte[] documento = documentoParaFirma(solicitud);
        Contribuyente contribuyente = contribuyenteService.obtenerPorCodigo(codigoContribuyente);
        return new DocumentoFirmaRespuesta(
                solicitud.getIdSolicitud(),
                "solicitud-constancia-" + solicitud.getIdSolicitud() + ".txt",
                TIPO_CONTENIDO_DOCUMENTO,
                Base64.getEncoder().encodeToString(documento),
                sha256(documento),
                firmador.esSimulado(),
                new DocumentoFirmaRespuesta.DatosContribuyente(contribuyente.getCodigoContribuyente(),
                        contribuyente.getNombreRazonSocial(), contribuyente.getTipoDocumento().name(),
                        contribuyente.getNumeroDocumento()));
    }

    @Transactional
    public SolicitudConstanciaRespuesta firmarSolicitud(String codigoContribuyente, Long idSolicitud,
            FirmaSolicitudConstancia firma) {
        SolicitudConstancia solicitud = buscarPropia(codigoContribuyente, idSolicitud);
        exigirPendienteDeFirma(solicitud);
        exigirSinDeuda(codigoContribuyente);

        ResultadoFirma resultado = firmador.verificarFirma(documentoParaFirma(solicitud),
                decodificarFirma(firma.firmaBase64()), firma.certificado().strip());
        if (!resultado.valida()) {
            LOG.info("Firma rechazada en la solicitud de constancia {}: {}", idSolicitud,
                    resultado.motivoRechazo());
            throw new ReglaNegocioException("FIRMA_INVALIDA",
                    "La firma digital no es válida: " + resultado.motivoRechazo()
                            + ". La solicitud no avanza hasta que firme con un certificado vigente.");
        }

        solicitud.registrarFirma(new FirmaDigital(TipoFirmante.CONTRIBUYENTE, resultado.entidadCertificadora(),
                LocalDateTime.now(reloj), true, resultado.hashDocumento()));
        return SolicitudConstanciaRespuesta.desde(solicitudConstanciaRepository.saveAndFlush(solicitud));
    }

    /* Una solicitud de otro contribuyente se responde igual que una inexistente, sin revelar datos (RNF-023). */
    private SolicitudConstancia buscarPropia(String codigoContribuyente, Long idSolicitud) {
        String documento = documentoDe(codigoContribuyente);
        return solicitudConstanciaRepository.findById(idSolicitud)
                .filter(solicitud -> solicitud.getDocumentoContribuyente().equals(documento))
                .orElseThrow(() -> new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                        "No tiene una solicitud de constancia con N.° " + idSolicitud + "."));
    }

    private String documentoDe(String codigoContribuyente) {
        return contribuyenteService.obtenerPorCodigo(codigoContribuyente).getNumeroDocumento();
    }

    public DeudaConstanciaRespuesta consultarDeuda(String codigoContribuyente) {
        var cuenta = samnet.obtenerEstadoCuenta(codigoContribuyente);
        if (cuenta.isDesdeCopia()) {
            throw new ReglaNegocioException("DEUDA_NO_VERIFICADA", "No se pudo verificar la deuda actual.");
        }
        var pendientes = cuenta.getObligaciones().stream().filter(o -> o.saldo().signum() > 0)
                .map(o -> EstadoCuentaFilaRespuesta.desde(o, cuenta.obtenerFechaCorte())).toList();
        BigDecimal total = pendientes.stream().map(EstadoCuentaFilaRespuesta::deuda)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DeudaConstanciaRespuesta(total, pendientes);
    }

    private void exigirSinDeuda(String codigoContribuyente) {
        if (!consultarDeuda(codigoContribuyente).puedeSolicitar()) {
            throw new ReglaNegocioException("DEUDA_PENDIENTE", "Tienes deuda pendiente. Consulta tu estado de cuenta antes de continuar.");
        }
    }

    private static void exigirPendienteDeFirma(SolicitudConstancia solicitud) {
        if (solicitud.getCanal() != CanalSolicitud.APP) {
            throw new ReglaNegocioException("FIRMA_NO_APLICA", "En ventanilla no se firma la solicitud.");
        }
        if (solicitud.getEstado() != EstadoConstancia.INICIADA) {
            throw new ReglaNegocioException("FIRMA_NO_PENDIENTE",
                    "La solicitud está " + solicitud.getEstado() + " y ya no requiere la firma del contribuyente.");
        }
    }

    /*
     * Documento que se firma. Se arma siempre con los mismos datos guardados, así el servidor verifica la firma
     * contra su propia versión y no contra un documento enviado por el cliente (RNF-029).
     */
    private byte[] documentoParaFirma(SolicitudConstancia solicitud) {
        StringBuilder texto = new StringBuilder();
        if (firmador.esSimulado()) {
            texto.append(MARCA_DOCUMENTO_DE_PRUEBA).append('\n');
        }
        texto.append("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL - GERENCIA DE RENTAS\n")
                .append("SOLICITUD DE CONSTANCIA DE NO ADEUDO\n")
                .append("Solicitud N.°: ").append(solicitud.getIdSolicitud()).append('\n')
                .append("Documento del contribuyente: ").append(solicitud.getDocumentoContribuyente()).append('\n')
                .append("Fecha de solicitud: ")
                .append(solicitud.getFechaSolicitud().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n')
                .append("Solicito la emisión de la Constancia de No Adeudo de mis tributos municipales y declaro ")
                .append("que los datos de esta solicitud son verdaderos.\n");
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodificarFirma(String firmaBase64) {
        try {
            return Base64.getDecoder().decode(firmaBase64.strip());
        } catch (IllegalArgumentException ex) {
            throw new DatoInvalidoException("FIRMA_MAL_CODIFICADA", "La firma debe enviarse codificada en Base64.");
        }
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
