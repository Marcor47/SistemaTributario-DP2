package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * HU042 (RF-055, RF-034, RF-037, RF-030): emisión de la Constancia de No Adeudo desde Ventanilla para
 * atención presencial al contribuyente con su DNI. Reutiliza los servicios del portal: consulta de deuda,
 * asignación automática de correlativo, firma digital automática de la Municipalidad, entrega al buzón y
 * registro obligatorio del trabajador de ventanilla emisor.
 */
@Service
@Transactional(readOnly = true)
public class ConstanciaVentanillaService {

    private static final Logger LOG = LoggerFactory.getLogger(ConstanciaVentanillaService.class);
    private static final String PREDIOS_DEFAULT = "Todos los predios del contribuyente";
    private static final String RANGO_DEFAULT = "Hasta la fecha de emisión";

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
     * Ejecuta el flujo de emisión presencial de constancia en ventanilla (RF-055, RF-034, RF-037):
     * 1. Valida deuda 0 en SAMNET. Si tiene deuda pendiente, se rechaza y se entrega el detalle.
     * 2. Registra la solicitud en canal VENTANILLA con el trabajador emisor.
     * 3. Registra y confirma el cobro del derecho de trámite.
     * 4. Asigna automáticamente el correlativo oficial (CNA-AAAA-NNNNNN).
     * 5. Incorpora la firma digital automática de la Municipalidad en el PDF oficial.
     * 6. Registra la constancia con el trabajador emisor.
     * 7. Deposita el documento en el buzón del contribuyente (RF-030).
     */
    @Transactional
    public ConstanciaVentanillaRespuesta emitir(EmitirConstanciaVentanillaSolicitud peticion, String nombreTrabajador) {
        Usuario trabajador = obtenerTrabajador(nombreTrabajador);
        Contribuyente contribuyente = resolverContribuyente(peticion.documentoContribuyente());

        DeudaConstanciaRespuesta deuda = solicitudConstanciaService.consultarDeuda(contribuyente.getCodigoContribuyente());
        if (!deuda.puedeSolicitar()) {
            LOG.warn("Intento de emisión de constancia en ventanilla denegado por deuda: contribuyente {}",
                    contribuyente.getNumeroDocumento());
            throw new ReglaNegocioException("DEUDA_PENDIENTE",
                    "El contribuyente registra deuda pendiente de S/ " + deuda.total()
                            + ". No se puede emitir la constancia de no adeudo; debe regularizar sus pagos.");
        }

        VersionPlantilla plantilla = versionPlantillaRepository
                .findByPlantillaCodigoAndVigenteTrue(ConstanciaOperadorService.CODIGO_PLANTILLA)
                .orElseThrow(() -> new ReglaNegocioException("PLANTILLA_NO_DISPONIBLE",
                        "No hay una versión vigente de la plantilla " + ConstanciaOperadorService.CODIGO_PLANTILLA + "."));

        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);

        // 1. Solicitud en canal Ventanilla con trabajador registrado
        SolicitudConstancia solicitud = new SolicitudConstancia(
                CanalSolicitud.VENTANILLA,
                contribuyente.getNumeroDocumento(),
                trabajador,
                ahora);
        solicitud = solicitudConstanciaRepository.save(solicitud);

        // 2. Verificación de deuda (en ventanilla el trabajador actúa como operador y no requiere firma previa)
        solicitud.verificarDeuda(trabajador, BigDecimal.ZERO, ahora);

        // 3. Cobro del derecho de trámite presencial en ventanilla
        TransaccionPago ordenPago = transaccionPagoRepository.save(
                TransaccionPago.deDerechoTramite(solicitud, derechoTramite, ahora));
        solicitud.emitirOrdenPago(ordenPago);

        ordenPago.confirmar("CONST-VENT-" + ordenPago.getIdTransaccion());
        transaccionPagoRepository.saveAndFlush(ordenPago);
        solicitud.confirmarPago(ordenPago);

        // 4. Parámetros de la constancia (RF-055: predios y rango de fechas elegibles por el trabajador)
        String prediosTexto = determinarPrediosTexto(peticion);
        String rangoFechasTexto = determinarRangoFechasTexto(peticion);

        // 5. Asignación automática del número correlativo
        String numeroConstancia = constanciaRepository.siguienteNumero(ahora.toLocalDate());

        // 6. Generación del documento PDF y firma digital de la Municipalidad (RF-037)
        byte[] pdf = generadorPdf.generar(numeroConstancia, contribuyente, ahora, prediosTexto, rangoFechasTexto,
                trabajador.getNombreCompleto(), firmador.esSimulado());
        FirmadorGateway.DocumentoFirmado firmado = firmador.firmarComoMunicipalidad(pdf);
        if (!firmado.firma().valida()) {
            throw new ReglaNegocioException("FIRMA_MUNICIPAL_INVALIDA",
                    "No se pudo firmar la constancia: " + firmado.firma().motivoRechazo() + ".");
        }

        solicitud.emitir(trabajador, new FirmaDigital(TipoFirmante.MUNICIPALIDAD,
                firmado.firma().entidadCertificadora(), ahora, true, firmado.firma().hashDocumento()), ahora);
        solicitud = solicitudConstanciaRepository.saveAndFlush(solicitud);

        // 7. Registro de la constancia emitida con el trabajador que la emitió
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

        // 8. Envío al buzón del contribuyente (RF-030)
        documentoBuzonRepository.save(
                DocumentoBuzon.deConstancia(constancia, contribuyente.getNumeroDocumento(), ahora));

        LOG.info("Constancia de No Adeudo {} emitida en ventanilla por {} para contribuyente {}",
                numeroConstancia, trabajador.getNombreUsuario(), contribuyente.getNumeroDocumento());

        return ConstanciaVentanillaRespuesta.desde(
                constancia,
                ordenPago,
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

