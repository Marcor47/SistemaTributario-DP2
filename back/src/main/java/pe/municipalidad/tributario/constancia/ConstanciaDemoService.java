package pe.municipalidad.tributario.constancia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.ContribuyenteService;
import pe.municipalidad.tributario.documentos.DocumentoBuzon;
import pe.municipalidad.tributario.documentos.DocumentoBuzonRepository;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;
import pe.municipalidad.tributario.integracion.firmador.FirmadorGateway;
import pe.municipalidad.tributario.integracion.niubiz.NiubizGateway;
import pe.municipalidad.tributario.integracion.niubiz.NiubizSimuladoGateway;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/** Flujo local con actor y pasarela simulados; no habilita emisión en producción. */
@Service
@ConditionalOnProperty(name = "tributario.constancia.demo.habilitado", havingValue = "true")
public class ConstanciaDemoService {
    private final SolicitudConstanciaRepository solicitudes;
    private final SolicitudConstanciaService solicitudService;
    private final TransaccionPagoRepository transacciones;
    private final ConstanciaRepository constancias;
    private final DocumentoBuzonRepository buzon;
    private final VersionPlantillaRepository plantillas;
    private final UsuarioRepository usuarios;
    private final ContribuyenteService contribuyentes;
    private final NiubizGateway pasarela;
    private final FirmadorGateway firmador;
    private final Clock reloj;
    private final BigDecimal derecho;

    public ConstanciaDemoService(SolicitudConstanciaRepository solicitudes, SolicitudConstanciaService solicitudService,
            TransaccionPagoRepository transacciones, ConstanciaRepository constancias, DocumentoBuzonRepository buzon,
            VersionPlantillaRepository plantillas, UsuarioRepository usuarios, ContribuyenteService contribuyentes,
            NiubizGateway pasarela, FirmadorGateway firmador, Clock reloj,
            @Value("${tributario.constancia.demo.derecho}") BigDecimal derecho) {
        this.solicitudes = solicitudes;
        this.solicitudService = solicitudService;
        this.transacciones = transacciones;
        this.constancias = constancias;
        this.buzon = buzon;
        this.plantillas = plantillas;
        this.usuarios = usuarios;
        this.contribuyentes = contribuyentes;
        this.pasarela = pasarela;
        this.firmador = firmador;
        this.reloj = reloj;
        if (derecho.signum() <= 0) throw new IllegalArgumentException("El derecho de prueba debe ser positivo.");
        this.derecho = derecho;
    }

    public record OrdenRespuesta(Long id, BigDecimal monto, String estado, String comprobante) {
        static OrdenRespuesta desde(TransaccionPago pago) {
            return new OrdenRespuesta(pago.getIdTransaccion(), pago.getMontoTotal(), pago.getEstado().name(), pago.getComprobante());
        }
    }
    public record DocumentoRespuesta(Long id, String titulo, LocalDateTime fechaEnvio, String numeroConstancia) {}

    @Transactional
    public OrdenRespuesta crearOrden(String codigo, Long id) {
        exigirDemo();
        SolicitudConstancia solicitud = propiaBloqueada(codigo, id);
        var existente = transacciones.findBySolicitudConstancia_IdSolicitud(id);
        if (existente.isPresent()) return OrdenRespuesta.desde(existente.get());
        exigirDeudaCero(codigo);
        if (solicitud.getEstado() != EstadoConstancia.FIRMADA) {
            throw new ReglaNegocioException("FIRMA_REQUERIDA", "Primero firma y envía la solicitud.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        solicitud.verificarDeuda(actor(), BigDecimal.ZERO, ahora);
        TransaccionPago pago = TransaccionPago.deDerechoTramite(solicitud, derecho, ahora);
        transacciones.saveAndFlush(pago);
        var orden = pasarela.crearOrdenPago(derecho, "CONSTANCIA-" + id);
        pago.iniciar(orden.idOrden());
        solicitud.emitirOrdenPago(pago);
        return OrdenRespuesta.desde(pago);
    }

    @Transactional(readOnly = true)
    public OrdenRespuesta consultarOrden(String codigo, Long id) {
        propia(codigo, id);
        return transacciones.findBySolicitudConstancia_IdSolicitud(id).map(OrdenRespuesta::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA", "No hay una orden para esta solicitud."));
    }

    @Transactional
    public OrdenRespuesta confirmarPago(String codigo, Long id) {
        exigirDemo();
        SolicitudConstancia solicitud = propiaBloqueada(codigo, id);
        TransaccionPago pago = transacciones.findBySolicitudConstancia_IdSolicitud(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("ORDEN_NO_ENCONTRADA", "Primero genera la orden de pago."));
        if (pago.getEstado() == EstadoTransaccion.CONFIRMADA) return OrdenRespuesta.desde(pago);
        if (solicitud.getEstado() != EstadoConstancia.ORDEN_PAGO_EMITIDA) {
            throw new ReglaNegocioException("ORDEN_NO_PENDIENTE", "La orden no está pendiente de pago.");
        }
        exigirDeudaCero(codigo);
        NiubizGateway.EstadoPagoPasarela respuesta;
        try {
            respuesta = pasarela.consultarEstadoPago(pago.getIdOrdenPasarela());
        } catch (RecursoNoEncontradoException ex) {
            // El simulador conserva sus órdenes en memoria; se repone la misma orden local tras un reinicio.
            pago.iniciar(pasarela.crearOrdenPago(pago.getMontoTotal(), "CONSTANCIA-" + id).idOrden());
            respuesta = pasarela.consultarEstadoPago(pago.getIdOrdenPasarela());
        }
        if (!respuesta.autorizado() || !pago.getIdOrdenPasarela().equals(respuesta.idOrden())
                || respuesta.montoAutorizado() == null || respuesta.montoAutorizado().compareTo(pago.getMontoTotal()) != 0) {
            throw new ReglaNegocioException("PAGO_NO_AUTORIZADO", "No se confirmó el pago.");
        }
        pago.confirmar("CONST-" + pago.getIdTransaccion());
        solicitud.confirmarPago(pago);
        return OrdenRespuesta.desde(pago);
    }

    @Transactional
    public DocumentoRespuesta emitir(String codigo, Long id) {
        exigirDemo();
        SolicitudConstancia solicitud = propiaBloqueada(codigo, id);
        var anterior = constancias.findBySolicitud_IdSolicitud(id);
        if (anterior.isPresent()) return documentoDeConstancia(solicitud.getDocumentoContribuyente(), anterior.get().getNumeroConstancia());
        if (solicitud.getEstado() != EstadoConstancia.PAGADA) {
            throw new ReglaNegocioException("PAGO_REQUERIDO", "Confirma el pago antes de emitir la constancia.");
        }
        exigirDeudaCero(codigo);
        var plantilla = plantillas.findByPlantillaCodigoAndVigenteTrue("CONSTANCIA_DEMO")
                .orElseThrow(() -> new ReglaNegocioException("PLANTILLA_NO_DISPONIBLE", "No hay plantilla de constancia disponible."));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String numero = constancias.siguienteNumero(ahora.toLocalDate());
        var contribuyente = contribuyentes.obtenerPorCodigo(codigo);
        byte[] pdf = generarPdf(numero, contribuyente.getNombreRazonSocial(), contribuyente.getNumeroDocumento(), ahora);
        var firmado = firmador.firmarComoMunicipalidad(pdf);
        if (!firmado.firma().valida()) throw new ReglaNegocioException("FIRMA_MUNICIPAL_INVALIDA", "No se pudo firmar la constancia.");
        Usuario operador = actor();
        solicitud.emitir(operador, new FirmaDigital(TipoFirmante.MUNICIPALIDAD,
                firmado.firma().entidadCertificadora(), ahora, true, firmado.firma().hashDocumento()), ahora);
        Constancia constancia = constancias.save(new Constancia(numero, solicitud, plantilla, ahora, "Todos los predios",
                "Hasta la fecha de emisión", numero + ".pdf", true, operador, firmado.contenido()));
        DocumentoBuzon entrega = buzon.save(DocumentoBuzon.deConstancia(constancia, solicitud.getDocumentoContribuyente(), ahora));
        return new DocumentoRespuesta(entrega.getIdDocumento(), entrega.getTitulo(), entrega.getFechaEnvio(), numero);
    }

    @Transactional(readOnly = true)
    public List<DocumentoRespuesta> listarBuzon(String codigo) {
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        return buzon.findByDocumentoContribuyenteOrderByFechaEnvioDesc(documento).stream()
                .filter(d -> d.getConstancia() != null && d.getConstancia().getContenidoPdf() != null)
                .map(d -> new DocumentoRespuesta(d.getIdDocumento(), d.getTitulo(), d.getFechaEnvio(),
                        d.getConstancia().getNumeroConstancia())).toList();
    }

    @Transactional
    public byte[] descargar(String codigo, Long id) {
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        DocumentoBuzon entrega = buzon.findById(id).filter(d -> documento.equals(d.getDocumentoContribuyente()))
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO", "No existe ese documento."));
        if (entrega.getConstancia() == null || entrega.getConstancia().getContenidoPdf() == null) {
            throw new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO", "El documento no está disponible.");
        }
        entrega.marcarLeido(LocalDateTime.now(reloj));
        return entrega.getConstancia().getContenidoPdf();
    }

    private DocumentoRespuesta documentoDeConstancia(String documento, String numero) {
        return buzon.findByDocumentoContribuyenteOrderByFechaEnvioDesc(documento).stream()
                .filter(d -> d.getConstancia() != null && numero.equals(d.getConstancia().getNumeroConstancia()))
                .findFirst().map(d -> new DocumentoRespuesta(d.getIdDocumento(), d.getTitulo(), d.getFechaEnvio(), numero))
                .orElseThrow(() -> new RecursoNoEncontradoException("DOCUMENTO_NO_ENCONTRADO", "La constancia no está en el buzón."));
    }

    private SolicitudConstancia propia(String codigo, Long id) {
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        return solicitudes.findById(id).filter(s -> documento.equals(s.getDocumentoContribuyente()))
                .orElseThrow(() -> new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA", "No existe esa solicitud."));
    }

    private SolicitudConstancia propiaBloqueada(String codigo, Long id) {
        String documento = contribuyentes.obtenerPorCodigo(codigo).getNumeroDocumento();
        return solicitudes.bloquearPorId(id).filter(s -> documento.equals(s.getDocumentoContribuyente()))
                .orElseThrow(() -> new RecursoNoEncontradoException("SOLICITUD_NO_ENCONTRADA", "No existe esa solicitud."));
    }

    private Usuario actor() {
        return usuarios.findByNombreUsuario("sistema.constancia")
                .orElseThrow(() -> new ReglaNegocioException("ACTOR_NO_DISPONIBLE", "No está configurado el proceso de constancias."));
    }

    private void exigirDemo() {
        if (!(pasarela instanceof NiubizSimuladoGateway) || !firmador.esSimulado()) {
            throw new ReglaNegocioException("DEMO_NO_DISPONIBLE", "Este flujo requiere los servicios de prueba configurados.");
        }
    }

    private void exigirDeudaCero(String codigo) {
        if (!solicitudService.consultarDeuda(codigo).puedeSolicitar()) {
            throw new ReglaNegocioException("DEUDA_PENDIENTE", "Tienes deuda pendiente; no se puede emitir la constancia.");
        }
    }

    private static byte[] generarPdf(String numero, String nombre, String documento, LocalDateTime fecha) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(55, 740);
                contenido.setLeading(22);
                for (String linea : List.of("DOCUMENTO DE PRUEBA - SIN VALIDEZ LEGAL",
                        "MUNICIPALIDAD DISTRITAL DE SAN MIGUEL", "CONSTANCIA DE NO ADEUDO",
                        "Número: " + numero, "Contribuyente: " + nombre, "Documento: " + documento,
                        "Fecha de emisión: " + fecha.toLocalDate(),
                        "Se deja constancia de que no registra deuda pendiente a la fecha.",
                        "Firma municipal simulada")) {
                    contenido.showText(linea);
                    contenido.newLine();
                }
                contenido.endText();
            }
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar la constancia.", ex);
        }
    }
}
