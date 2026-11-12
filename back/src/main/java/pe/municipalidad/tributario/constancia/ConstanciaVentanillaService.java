package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU042 por pasos (RF-055, RF-034, RF-021, RF-037, RF-030): emisión presencial de la Constancia de No Adeudo.
 * El trabajador crea la solicitud, el contribuyente firma en el dispositivo presencial y la firma llega al
 * sistema; luego se verifica la deuda, se genera la orden de pago, se confirma el cobro presencial y se emite
 * la constancia, que la firma la Municipalidad y va al buzón. Cada paso exige el anterior (RNF-029).
 */
@Service
@Transactional(readOnly = true)
public class ConstanciaVentanillaService {

    private static final Logger LOG = LoggerFactory.getLogger(ConstanciaVentanillaService.class);
    private static final String PREDIOS_DEFAULT = "Todos los predios del contribuyente";
    private static final String RANGO_DEFAULT = "Hasta la fecha de emisión";
    private static final Set<EstadoConstancia> ESTADOS_CERRADOS =
            EnumSet.of(EstadoConstancia.EMITIDA, EstadoConstancia.RECHAZADA);

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

    public ConstanciaVentanillaService(
            SolicitudConstanciaRepository solicitudConstanciaRepository,
            SolicitudConstanciaService solicitudConstanciaService,
            TransaccionPagoRepository transaccionPagoRepository,
            ConstanciaRepository constanciaRepository,
            DocumentoBuzonRepository documentoBuzonRepository,
            VersionPlantillaRepository versionPlantillaRepository,
            UsuarioRepository usuarioRepository,
            SamnetGateway samnet,
            FirmadorGateway firmador,
            GeneradorConstanciaPdf generadorPdf,
            Clock reloj,
            @Value("${tributario.constancia.derecho-tramite}") BigDecimal derechoTramite) {
        if (derechoTramite == null || derechoTramite.signum() <= 0) {
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
     * Consulta previa con el DNI o código del contribuyente. Obtiene los datos del titular, la lista de predios
     * registrados para selección opcional en ventanilla y el estado de su deuda en SAMNET (RF-055, RF-034).
     */
    public ConsultaContribuyenteVentanillaRespuesta consultar(String documento) {
        Contribuyente contribuyente = resolverContribuyente(documento);
        List<Predio> predios = samnet.buscarPredio(null, contribuyente.getCodigoContribuyente());
        List<PredioResumenVentanilla> prediosDto = predios.stream().map(PredioResumenVentanilla::desde).toList();
        DeudaConstanciaRespuesta deuda = solicitudConstanciaService.consultarDeuda(contribuyente.getCodigoContribuyente());
        return new ConsultaContribuyenteVentanillaRespuesta(
                ConsultaContribuyenteVentanillaRespuesta.DatosContribuyenteDto.desde(contribuyente),
                prediosDto,
                deuda);
    }

    /*
     * Solicitudes del contribuyente para retomar una abierta o revisar el resultado de una cerrada. Se listan ambos
     * canales y todos los estados, de la más reciente a la más antigua (HU042).
     */
    public List<SolicitudVentanillaRespuesta> listarSolicitudes(String documentoContribuyente) {
        Contribuyente contribuyente = resolverContribuyente(documentoContribuyente);
        return solicitudConstanciaRepository
                .findTop20ByDocumentoContribuyenteOrderByFechaSolicitudDesc(contribuyente.getNumeroDocumento())
                .stream()
                .map(solicitud -> respuesta(solicitud, contribuyente, ordenDe(solicitud), null))
                .toList();
    }

    /*
     * Presenta la solicitud en ventanilla. Si el contribuyente ya tiene una abierta, se retoma sin volver a evaluar
     * la deuda; si es nueva, se evalúa en este momento (la presentación) y se congela el resultado (HU042). Con
     * deuda, la solicitud queda RECHAZADA con su motivo y el detalle de lo pendiente.
     */
    @Transactional
    public SolicitudVentanillaRespuesta crearSolicitud(String documentoContribuyente, String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        Contribuyente contribuyente = resolverContribuyente(documentoContribuyente);
        String documento = contribuyente.getNumeroDocumento();
        var abierta = solicitudConstanciaRepository
                .findFirstByDocumentoContribuyenteAndCanalAndEstadoNotInOrderByFechaSolicitudDesc(
                        documento, CanalSolicitud.VENTANILLA, ESTADOS_CERRADOS);
        if (abierta.isPresent()) {
            return respuesta(abierta.get(), contribuyente, ordenDe(abierta.get()), null);
        }

        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        DeudaConstanciaRespuesta deuda = solicitudConstanciaService.consultarDeuda(contribuyente.getCodigoContribuyente());
        SolicitudConstancia solicitud = new SolicitudConstancia(CanalSolicitud.VENTANILLA, documento, trabajador, ahora);
        solicitud.registrarDeudaPresentacion(deuda.total(), ahora);
        if (!deuda.puedeSolicitar()) {
            LOG.warn("Solicitud de constancia de ventanilla rechazada al presentar por deuda: contribuyente {}",
                    documento);
            solicitud.rechazar(trabajador, "El contribuyente tiene deuda pendiente de S/ " + deuda.total() + ".",
                    ahora);
        } else {
            LOG.info("Nueva solicitud de constancia en ventanilla para el contribuyente {}", documento);
        }
        return respuesta(solicitudConstanciaRepository.save(solicitud), contribuyente, null, deuda);
    }

    /* Documento de la solicitud que se muestra en el dispositivo de firma presencial (RF-021). */
    public DocumentoFirmaRespuesta obtenerDocumentoFirma(Long idSolicitud) {
        SolicitudConstancia solicitud = solicitudVentanilla(idSolicitud, EstadoConstancia.INICIADA);
        byte[] documento = solicitudConstanciaService.documentoParaFirma(solicitud);
        Contribuyente contribuyente = resolverContribuyente(solicitud.getDocumentoContribuyente());
        return new DocumentoFirmaRespuesta(
                solicitud.getIdSolicitud(),
                "solicitud-constancia-" + solicitud.getIdSolicitud() + ".txt",
                SolicitudConstanciaService.TIPO_CONTENIDO_DOCUMENTO,
                Base64.getEncoder().encodeToString(documento),
                SolicitudConstanciaService.sha256(documento),
                firmador.esSimulado(),
                new DocumentoFirmaRespuesta.DatosContribuyente(contribuyente.getCodigoContribuyente(),
                        contribuyente.getNombreRazonSocial(), contribuyente.getTipoDocumento().name(),
                        contribuyente.getNumeroDocumento()));
    }

    /* La firma la captura el dispositivo presencial frente al contribuyente; el servidor la verifica contra su documento. */
    @Transactional
    public SolicitudVentanillaRespuesta registrarFirma(Long idSolicitud, FirmaSolicitudConstancia firma,
            String nombreTrabajador) {
        obtenerTrabajador(nombreTrabajador);
        SolicitudConstancia solicitud = solicitudVentanilla(idSolicitud, EstadoConstancia.INICIADA);
        FirmadorGateway.ResultadoFirma resultado = firmador.verificarFirma(
                solicitudConstanciaService.documentoParaFirma(solicitud),
                SolicitudConstanciaService.decodificarFirma(firma.firmaBase64()), firma.certificado().strip());
        if (!resultado.valida()) {
            LOG.info("Firma rechazada en la solicitud de constancia de ventanilla {}: {}", idSolicitud,
                    resultado.motivoRechazo());
            throw new ReglaNegocioException("FIRMA_INVALIDA",
                    "La firma digital no es válida: " + resultado.motivoRechazo()
                            + ". La solicitud no avanza hasta que el contribuyente firme con un certificado vigente.");
        }
        solicitud.registrarFirma(new FirmaDigital(TipoFirmante.CONTRIBUYENTE, resultado.entidadCertificadora(),
                LocalDateTime.now(reloj), true, resultado.hashDocumento()));
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), ordenDe(solicitud), null);
    }

    /*
     * Fase 3: con la deuda ya congelada al presentar la solicitud, genera la orden de pago del derecho de trámite.
     * Solo las solicitudes anteriores a esta regla (sin evaluación) se consultan una única vez en SAMNET y el
     * resultado se guarda para no volver a verificarlo (HU042, RF-034, RF-036).
     */
    @Transactional
    public SolicitudVentanillaRespuesta verificarDeuda(Long idSolicitud, String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        SolicitudConstancia solicitud = bloquearVentanilla(idSolicitud);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        DeudaConstanciaRespuesta deuda;
        if (solicitud.getDeudaPresentacion() == null) {
            deuda = solicitudConstanciaService.consultarDeuda(codigoDe(solicitud));
            solicitud.registrarDeudaPresentacion(deuda.total(), ahora);
        } else {
            deuda = new DeudaConstanciaRespuesta(solicitud.getDeudaPresentacion(), List.of());
        }
        boolean continua = solicitud.verificarDeuda(trabajador, deuda.total(), ahora);
        TransaccionPago ordenPago = null;
        if (continua) {
            ordenPago = transaccionPagoRepository.save(
                    TransaccionPago.deDerechoTramite(solicitud, derechoTramite, ahora));
            solicitud.emitirOrdenPago(ordenPago);
        } else {
            LOG.warn("Solicitud de constancia {} de ventanilla rechazada por deuda pendiente", idSolicitud);
        }
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), ordenPago, deuda);
    }

    /* Orden de pago del derecho de trámite asociada a la solicitud de ventanilla. */
    public OrdenPagoConstanciaRespuesta obtenerOrdenPago(Long idSolicitud) {
        SolicitudConstancia solicitud = solicitudVentanilla(idSolicitud, null);
        TransaccionPago ordenPago = transaccionPagoRepository
                .findBySolicitudConstancia_IdSolicitud(idSolicitud)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA",
                        "Aún no se genera la orden de pago de la solicitud N.° " + idSolicitud + "."));
        return OrdenPagoConstanciaRespuesta.desde(ordenPago, null, null);
    }

    /* El trabajador confirma el cobro presencial del derecho de trámite (RF-055). */
    @Transactional
    public SolicitudVentanillaRespuesta confirmarPago(Long idSolicitud, String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        SolicitudConstancia solicitud = bloquearVentanilla(idSolicitud);
        if (solicitud.getEstado() != EstadoConstancia.ORDEN_PAGO_EMITIDA) {
            throw new ReglaNegocioException("ORDEN_NO_PENDIENTE",
                    "La orden de pago de la solicitud N.° " + idSolicitud + " no está pendiente.");
        }
        TransaccionPago ordenPago = transaccionPagoRepository
                .findBySolicitudConstancia_IdSolicitud(idSolicitud)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA",
                        "No hay una orden de pago para la solicitud N.° " + idSolicitud + "."));
        ordenPago.confirmar("CONST-VENT-" + ordenPago.getIdTransaccion());
        transaccionPagoRepository.saveAndFlush(ordenPago);
        solicitud.confirmarPago(ordenPago);
        return respuesta(solicitudConstanciaRepository.saveAndFlush(solicitud), ordenPago, null);
    }

    /*
     * Emisión de la constancia tras el pago confirmado: se usa la deuda congelada al presentar la solicitud (no se
     * vuelve a consultar SAMNET), se asigna el correlativo, se genera el PDF con la firma municipal y se envía al
     * buzón del contribuyente (RF-055, RF-037, RF-030).
     */
    @Transactional
    public ConstanciaVentanillaRespuesta emitir(Long idSolicitud, EmitirConstanciaVentanillaSolicitud peticion,
            String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        SolicitudConstancia solicitud = bloquearVentanilla(idSolicitud);
        if (solicitud.getEstado() != EstadoConstancia.PAGADA) {
            throw new ReglaNegocioException("PAGO_REQUERIDO",
                    "La solicitud está " + solicitud.getEstado()
                            + ": la constancia se emite tras el pago del trámite.");
        }
        if (solicitud.getDeudaPresentacion() == null || solicitud.getDeudaPresentacion().signum() > 0) {
            throw new ReglaNegocioException("DEUDA_NO_VERIFICADA",
                    "No se registró una evaluación sin adeudo al presentar la solicitud N.° " + idSolicitud + ".");
        }
        Contribuyente contribuyente = resolverContribuyente(solicitud.getDocumentoContribuyente());

        VersionPlantilla plantilla = versionPlantillaRepository
                .findByPlantillaCodigoAndVigenteTrue(ConstanciaOperadorService.CODIGO_PLANTILLA)
                .orElseThrow(() -> new ReglaNegocioException("PLANTILLA_NO_DISPONIBLE",
                        "No hay una versión vigente de la plantilla " + ConstanciaOperadorService.CODIGO_PLANTILLA + "."));

        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        String prediosTexto = determinarPrediosTexto(peticion);
        String rangoFechasTexto = determinarRangoFechasTexto(peticion);
        String numeroConstancia = constanciaRepository.siguienteNumero(ahora.toLocalDate());

        byte[] pdf = generadorPdf.generar(numeroConstancia, contribuyente, ahora,
                solicitud.getFechaSolicitud().toLocalDate(), prediosTexto, rangoFechasTexto,
                trabajador.getNombreCompleto(), firmador.esSimulado());
        FirmadorGateway.DocumentoFirmado firmado = firmador.firmarComoMunicipalidad(pdf);
        if (!firmado.firma().valida()) {
            throw new ReglaNegocioException("FIRMA_MUNICIPAL_INVALIDA",
                    "No se pudo firmar la constancia: " + firmado.firma().motivoRechazo() + ".");
        }

        solicitud.emitir(trabajador, new FirmaDigital(TipoFirmante.MUNICIPALIDAD,
                firmado.firma().entidadCertificadora(), ahora, true, firmado.firma().hashDocumento()), ahora);
        solicitudConstanciaRepository.saveAndFlush(solicitud);

        Constancia constancia = constanciaRepository.save(new Constancia(
                numeroConstancia,
                solicitud,
                plantilla,
                ahora,
                prediosTexto,
                rangoFechasTexto,
                numeroConstancia + ".pdf",
                firmador.esSimulado(),
                trabajador,
                firmado.contenido()));

        documentoBuzonRepository.save(
                DocumentoBuzon.deConstancia(constancia, solicitud.getDocumentoContribuyente(), ahora));

        LOG.info("Constancia de No Adeudo {} emitida en ventanilla por {} para contribuyente {}",
                numeroConstancia, trabajador.getNombreUsuario(), contribuyente.getNumeroDocumento());

        return ConstanciaVentanillaRespuesta.desde(
                constancia,
                transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(idSolicitud).orElse(null),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getCodigoContribuyente());
    }

    /* Obtención del PDF firmado para impresión o entrega física en ventanilla (RF-055). */
    public byte[] obtenerPdf(String numeroConstancia) {
        Constancia constancia = constanciaRepository.findById(numeroConstancia)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONSTANCIA_NO_ENCONTRADA",
                        "No existe la constancia N.° " + numeroConstancia + "."));
        if (constancia.getContenidoPdf() == null || constancia.getContenidoPdf().length == 0) {
            throw new RecursoNoEncontradoException("PDF_NO_DISPONIBLE",
                    "El contenido PDF de la constancia N.° " + numeroConstancia + " no está disponible.");
        }
        return constancia.getContenidoPdf();
    }

    /* Listado histórico de constancias emitidas en ventanilla. */
    public List<ConstanciaVentanillaRespuesta> listar(String documentoContribuyente) {
        List<Constancia> constancias = (documentoContribuyente != null && !documentoContribuyente.isBlank())
                ? constanciaRepository.findBySolicitud_DocumentoContribuyenteOrderByFechaEmisionDesc(documentoContribuyente.strip())
                : constanciaRepository.findTop50ByOrderByFechaEmisionDesc();

        return constancias.stream().map(constancia -> {
            TransaccionPago ordenPago = transaccionPagoRepository
                    .findBySolicitudConstancia_IdSolicitud(constancia.getSolicitud().getIdSolicitud())
                    .orElse(null);
            String nombre = null;
            String codigo = null;
            try {
                Contribuyente c = resolverContribuyente(constancia.getSolicitud().getDocumentoContribuyente());
                nombre = c.getNombreRazonSocial();
                codigo = c.getCodigoContribuyente();
            } catch (Exception ignored) {
                // Si SAMNET no tiene datos en este momento se mantiene el documento original
            }
            return ConstanciaVentanillaRespuesta.desde(constancia, ordenPago, nombre, codigo);
        }).toList();
    }

    private SolicitudVentanillaRespuesta respuesta(SolicitudConstancia solicitud, TransaccionPago ordenPago,
            DeudaConstanciaRespuesta deuda) {
        return respuesta(solicitud, resolverContribuyente(solicitud.getDocumentoContribuyente()), ordenPago,
                deuda != null ? deuda : deudaPresentacion(solicitud));
    }

    private SolicitudVentanillaRespuesta respuesta(SolicitudConstancia solicitud, Contribuyente contribuyente,
            TransaccionPago ordenPago, DeudaConstanciaRespuesta deuda) {
        return SolicitudVentanillaRespuesta.desde(solicitud, contribuyente, ordenPago,
                deuda != null ? deuda : deudaPresentacion(solicitud), numeroDe(solicitud));
    }

    /* La evaluación congelada al presentar se expone como deuda del trámite; el detalle solo se conoce al evaluarla. */
    private static DeudaConstanciaRespuesta deudaPresentacion(SolicitudConstancia solicitud) {
        return solicitud.getDeudaPresentacion() == null
                ? null
                : new DeudaConstanciaRespuesta(solicitud.getDeudaPresentacion(), List.of());
    }

    private TransaccionPago ordenDe(SolicitudConstancia solicitud) {
        return transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(solicitud.getIdSolicitud())
                .orElse(null);
    }

    private String numeroDe(SolicitudConstancia solicitud) {
        return constanciaRepository.findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .map(Constancia::getNumeroConstancia).orElse(null);
    }

    private SolicitudConstancia solicitudVentanilla(Long idSolicitud, EstadoConstancia esperado) {
        SolicitudConstancia solicitud = solicitudConstanciaRepository.findById(idSolicitud)
                .filter(solicitudActual -> solicitudActual.getCanal() == CanalSolicitud.VENTANILLA)
                .orElseThrow(() -> noEncontrada(idSolicitud));
        if (esperado != null && solicitud.getEstado() != esperado) {
            throw new ReglaNegocioException("SECUENCIA_INVALIDA",
                    "El trámite está en " + solicitud.getEstado() + " y este paso requiere " + esperado + ".");
        }
        return solicitud;
    }

    private SolicitudConstancia bloquearVentanilla(Long idSolicitud) {
        return solicitudConstanciaRepository.bloquearPorId(idSolicitud)
                .filter(solicitud -> solicitud.getCanal() == CanalSolicitud.VENTANILLA)
                .orElseThrow(() -> noEncontrada(idSolicitud));
    }

    private static RecursoNoEncontradoException noEncontrada(Long idSolicitud) {
        return new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA",
                "No existe la solicitud de constancia N.° " + idSolicitud + ".");
    }

    private String codigoDe(SolicitudConstancia solicitud) {
        return resolverContribuyente(solicitud.getDocumentoContribuyente()).getCodigoContribuyente();
    }

    private Contribuyente resolverContribuyente(String documentoOCodigo) {
        if (documentoOCodigo == null || documentoOCodigo.isBlank()) {
            throw new DatoInvalidoException("DOCUMENTO_REQUERIDO",
                    "El DNI o documento del contribuyente es obligatorio.");
        }
        String doc = documentoOCodigo.strip();
        return samnet.buscarContribuyente(doc).stream()
                .filter(c -> doc.equalsIgnoreCase(c.getNumeroDocumento())
                        || doc.equalsIgnoreCase(c.getCodigoContribuyente()))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No se encontró un contribuyente registrado con el documento o código " + doc + "."));
    }

    private Usuario obtenerTrabajador(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new NoAutenticadoException("TRABAJADOR_NO_IDENTIFICADO",
                        "Inicie sesión con un usuario interno activo."));
    }

    private static String determinarPrediosTexto(EmitirConstanciaVentanillaSolicitud peticion) {
        String texto = PREDIOS_DEFAULT;
        if (peticion.predios() != null && !peticion.predios().isEmpty()) {
            texto = String.join(", ", peticion.predios());
        } else if (peticion.prediosTexto() != null && !peticion.prediosTexto().isBlank()) {
            texto = peticion.prediosTexto().strip();
        }
        return texto.length() > 400 ? texto.substring(0, 400) : texto;
    }

    private static String determinarRangoFechasTexto(EmitirConstanciaVentanillaSolicitud peticion) {
        String rango = (peticion.rangoFechas() != null && !peticion.rangoFechas().isBlank())
                ? peticion.rangoFechas().strip()
                : RANGO_DEFAULT;
        return rango.length() > 60 ? rango.substring(0, 60) : rango;
    }
}
