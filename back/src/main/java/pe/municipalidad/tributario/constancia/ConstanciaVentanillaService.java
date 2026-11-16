package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
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
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/*
 * HU042 por pasos (RF-055, RF-034, RF-021, RF-037, RF-030): emisión presencial de la Constancia de No Adeudo.
 * El trabajador crea la solicitud, el contribuyente firma en el dispositivo presencial y la firma llega al
 * sistema; luego se verifica la deuda y se genera la orden de pago. Al confirmar el cobro presencial se emite
 * automáticamente la constancia, la firma la Municipalidad y va al buzón. Cada paso exige el anterior (RNF-029).
 */
@Service
@Transactional(readOnly = true)
public class ConstanciaVentanillaService {

    private static final Logger LOG = LoggerFactory.getLogger(ConstanciaVentanillaService.class);
    private static final String PREDIOS_DEFAULT = "Todos los predios del contribuyente";
    private static final String RANGO_DEFAULT = "Hasta la fecha de emisión";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<EstadoConstancia> ESTADOS_CERRADOS =
            EnumSet.of(EstadoConstancia.EMITIDA, EstadoConstancia.RECHAZADA);

    private final SolicitudConstanciaRepository solicitudConstanciaRepository;
    private final SolicitudConstanciaService solicitudConstanciaService;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ConstanciaRepository constanciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SamnetGateway samnet;
    private final FirmadorGateway firmador;
    private final GeneradorConstanciaPdf generadorPdf;
    private final EmisorConstancia emisorConstancia;
    private final Clock reloj;
    private final BigDecimal derechoTramite;

    public ConstanciaVentanillaService(
            SolicitudConstanciaRepository solicitudConstanciaRepository,
            SolicitudConstanciaService solicitudConstanciaService,
            TransaccionPagoRepository transaccionPagoRepository,
            ConstanciaRepository constanciaRepository,
            UsuarioRepository usuarioRepository,
            SamnetGateway samnet,
            FirmadorGateway firmador,
            GeneradorConstanciaPdf generadorPdf,
            EmisorConstancia emisorConstancia,
            Clock reloj,
            @Value("${tributario.constancia.derecho-tramite}") BigDecimal derechoTramite) {
        if (derechoTramite == null || derechoTramite.signum() <= 0) {
            throw new IllegalArgumentException("El derecho de trámite de la constancia debe ser positivo.");
        }
        this.solicitudConstanciaRepository = solicitudConstanciaRepository;
        this.solicitudConstanciaService = solicitudConstanciaService;
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.constanciaRepository = constanciaRepository;
        this.usuarioRepository = usuarioRepository;
        this.samnet = samnet;
        this.firmador = firmador;
        this.generadorPdf = generadorPdf;
        this.emisorConstancia = emisorConstancia;
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
                firmador.firmaLocal(),
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

    /* El trabajador confirma el cobro y el sistema emite/firma en la misma operación (RF-055, RF-037). */
    @Transactional
    public ConstanciaVentanillaRespuesta confirmarPago(Long idSolicitud,
            EmitirConstanciaVentanillaSolicitud peticion, String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        SolicitudConstancia solicitud = bloquearVentanilla(idSolicitud);

        if (solicitud.getEstado() == EstadoConstancia.EMITIDA) {
            return respuestaEmitida(solicitud);
        }
        if (solicitud.getEstado() != EstadoConstancia.ORDEN_PAGO_EMITIDA
                && solicitud.getEstado() != EstadoConstancia.PAGADA) {
            throw new ReglaNegocioException("ORDEN_NO_PENDIENTE",
                    "La orden de pago de la solicitud N.° " + idSolicitud + " no está pendiente.");
        }
        Contribuyente contribuyente = resolverContribuyente(solicitud.getDocumentoContribuyente());
        ParametrosEmision parametros = validarParametrosEmision(contribuyente, peticion);
        TransaccionPago ordenPago = transaccionPagoRepository
                .findBySolicitudConstancia_IdSolicitud(idSolicitud)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA",
                        "No hay una orden de pago para la solicitud N.° " + idSolicitud + "."));
        if (solicitud.getEstado() == EstadoConstancia.ORDEN_PAGO_EMITIDA) {
            ordenPago.confirmar("CONST-VENT-" + ordenPago.getIdTransaccion());
            transaccionPagoRepository.saveAndFlush(ordenPago);
            solicitud.confirmarPago(ordenPago);
        }
        return emitirPagada(solicitud, contribuyente, trabajador, parametros, ordenPago);
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
        if (solicitud.getEstado() == EstadoConstancia.EMITIDA) {
            return respuestaEmitida(solicitud);
        }
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
        ParametrosEmision parametros = validarParametrosEmision(contribuyente, peticion);
        TransaccionPago ordenPago = transaccionPagoRepository.findBySolicitudConstancia_IdSolicitud(idSolicitud)
                .orElse(null);
        return emitirPagada(solicitud, contribuyente, trabajador, parametros, ordenPago);
    }

    /* Obtención del PDF firmado para impresión o entrega física en ventanilla (RF-055). */
    public byte[] obtenerPdf(String numeroConstancia) {
        Constancia constancia = constanciaRepository.findById(numeroConstancia)
                .orElseThrow(() -> new RecursoNoEncontradoException("CONSTANCIA_NO_ENCONTRADA",
                        "No existe la constancia N.° " + numeroConstancia + "."));
        if (constancia.getContenidoPdf() == null || constancia.getContenidoPdf().length == 0) {
            /* Constancias registradas sin archivo: se genera el mismo documento con sus datos. */
            return generadorPdf.generar(constancia.getNumeroConstancia(),
                    resolverContribuyente(constancia.getSolicitud().getDocumentoContribuyente()),
                    constancia.getFechaEmision(), constancia.getPrediosIncluidos(), constancia.getRangoFechas(),
                    constancia.getTrabajadorEmisor() == null ? null : constancia.getTrabajadorEmisor().getNombreCompleto(),
                    false);
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

    private ConstanciaVentanillaRespuesta emitirPagada(SolicitudConstancia solicitud, Contribuyente contribuyente,
            Usuario trabajador, ParametrosEmision parametros, TransaccionPago ordenPago) {
        if (solicitud.getDeudaPresentacion() == null || solicitud.getDeudaPresentacion().signum() > 0) {
            throw new ReglaNegocioException("DEUDA_NO_VERIFICADA",
                    "No se registró una evaluación sin adeudo al presentar la solicitud N.° "
                            + solicitud.getIdSolicitud() + ".");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        EmisorConstancia.ConstanciaEmitida emitida = emisorConstancia.emitir(solicitud, contribuyente, trabajador,
                ahora, parametros.prediosTexto(), parametros.rangoFechasTexto());
        solicitudConstanciaRepository.saveAndFlush(solicitud);
        LOG.info("Constancia de No Adeudo {} emitida automáticamente en ventanilla por {} para contribuyente {}",
                emitida.constancia().getNumeroConstancia(), trabajador.getNombreUsuario(),
                contribuyente.getNumeroDocumento());
        return ConstanciaVentanillaRespuesta.desde(emitida.constancia(), ordenPago,
                contribuyente.getNombreRazonSocial(), contribuyente.getCodigoContribuyente());
    }

    private ConstanciaVentanillaRespuesta respuestaEmitida(SolicitudConstancia solicitud) {
        Constancia constancia = constanciaRepository.findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .orElseThrow(() -> new RecursoNoEncontradoException("CONSTANCIA_NO_ENCONTRADA",
                        "La solicitud está emitida pero no se encontró su constancia."));
        Contribuyente contribuyente = resolverContribuyente(solicitud.getDocumentoContribuyente());
        return ConstanciaVentanillaRespuesta.desde(constancia, ordenDe(solicitud),
                contribuyente.getNombreRazonSocial(), contribuyente.getCodigoContribuyente());
    }

    private ParametrosEmision validarParametrosEmision(Contribuyente contribuyente,
            EmitirConstanciaVentanillaSolicitud peticion) {
        EmitirConstanciaVentanillaSolicitud parametros = peticion == null
                ? EmitirConstanciaVentanillaSolicitud.valoresPorDefecto() : peticion;
        if (!parametros.isRangoFechasValido()) {
            throw new DatoInvalidoException("RANGO_FECHAS_INVALIDO",
                    "Indique ambas fechas y asegúrese de que la fecha final no sea anterior a la inicial.");
        }

        LinkedHashSet<String> seleccionados = new LinkedHashSet<>();
        if (parametros.predios() != null) {
            parametros.predios().stream().filter(java.util.Objects::nonNull).map(String::strip)
                    .filter(codigo -> !codigo.isBlank())
                    .forEach(seleccionados::add);
        }
        if (!seleccionados.isEmpty()) {
            Set<String> permitidos = samnet.buscarPredio(null, contribuyente.getCodigoContribuyente()).stream()
                    .map(Predio::getCodigoPredio).collect(java.util.stream.Collectors.toSet());
            List<String> ajenos = seleccionados.stream().filter(codigo -> !permitidos.contains(codigo)).toList();
            if (!ajenos.isEmpty()) {
                throw new ReglaNegocioException("PREDIO_NO_PERTENECE",
                        "Los predios seleccionados no pertenecen al contribuyente: " + String.join(", ", ajenos) + ".");
            }
        }

        String prediosTexto = seleccionados.isEmpty() ? PREDIOS_DEFAULT : String.join(", ", seleccionados);
        if (prediosTexto.length() > 400) {
            throw new DatoInvalidoException("PREDIOS_EXCEDEN_DOCUMENTO",
                    "La selección de predios excede los 400 caracteres permitidos en la constancia.");
        }
        String rangoTexto = parametros.fechaDesde() == null ? RANGO_DEFAULT
                : "Del " + parametros.fechaDesde().format(FORMATO_FECHA) + " al "
                        + parametros.fechaHasta().format(FORMATO_FECHA);
        return new ParametrosEmision(prediosTexto, rangoTexto);
    }

    private record ParametrosEmision(String prediosTexto, String rangoFechasTexto) {}
}
