package pe.municipalidad.tributario.documentos;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.constancia.Constancia;
import pe.municipalidad.tributario.constancia.GeneradorConstanciaPdf;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.documentos.DocumentoInstitucionalPdf.Dato;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.tramitepredio.EstadoSolicitudPredio;
import pe.municipalidad.tributario.tramitepredio.SolicitudPredio;

/*
 * PDF de los documentos del buzón con la plantilla institucional (5.3): la Constancia de No Adeudo (la firmada que se
 * guardó al emitirla o, si no se guardó, la misma generada con sus datos) y la resolución de una solicitud de
 * registro o traspaso de predio.
 */
@Service
public class DocumentoBuzonPdfService {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GeneradorConstanciaPdf generadorConstancia;
    private final SamnetGateway samnet;

    public DocumentoBuzonPdfService(GeneradorConstanciaPdf generadorConstancia, SamnetGateway samnet) {
        this.generadorConstancia = generadorConstancia;
        this.samnet = samnet;
    }

    public byte[] constancia(Constancia constancia) {
        if (constancia.getContenidoPdf() != null && constancia.getContenidoPdf().length > 0) {
            return constancia.getContenidoPdf();
        }
        Contribuyente contribuyente = contribuyente(constancia.getSolicitud().getDocumentoContribuyente());
        return generadorConstancia.generar(constancia.getNumeroConstancia(), contribuyente,
                constancia.getFechaEmision(), constancia.getPrediosIncluidos(), constancia.getRangoFechas(),
                constancia.getTrabajadorEmisor() == null ? null : constancia.getTrabajadorEmisor().getNombreCompleto(),
                false);
    }

    public byte[] respuestaSolicitudPredio(SolicitudPredio solicitud) {
        Contribuyente contribuyente = contribuyente(solicitud.getDocumentoContribuyente());
        boolean aceptada = solicitud.getEstado() == EstadoSolicitudPredio.ACEPTADA;
        String tramite = switch (solicitud.getTipoTramite()) {
            case REGISTRO -> "registro de predio";
            case TRASPASO -> "traspaso de predio";
        };
        String codigo = "RSP-" + solicitud.getFechaEnvio().getYear() + "-" + String.format("%06d", solicitud.getIdSolicitud());
        DocumentoInstitucionalPdf documento = new DocumentoInstitucionalPdf("Gerencia de Rentas - Registro Tributario",
                "Resolución de solicitud de " + tramite, codigo,
                solicitud.getFechaRevision() != null ? solicitud.getFechaRevision() : solicitud.getFechaEnvio());
        documento.seccion("Datos del solicitante").datos(List.of(
                new Dato("Contribuyente:", contribuyente == null ? solicitud.getDocumentoContribuyente()
                        : contribuyente.getNombreRazonSocial()),
                new Dato("Documento:", contribuyente == null ? solicitud.getDocumentoContribuyente()
                        : contribuyente.getTipoDocumento().name() + " " + contribuyente.getNumeroDocumento()),
                new Dato("Código de contribuyente:", contribuyente == null ? null : contribuyente.getCodigoContribuyente()),
                new Dato("Domicilio fiscal:", contribuyente == null || contribuyente.getDomicilioFiscal() == null ? null
                        : contribuyente.getDomicilioFiscal().direccion())));
        documento.seccion("Datos del trámite").datos(List.of(
                new Dato("Solicitud N.°:", String.valueOf(solicitud.getIdSolicitud())),
                new Dato("Tipo de trámite:", Character.toUpperCase(tramite.charAt(0)) + tramite.substring(1)),
                new Dato("Tipo de predio:", solicitud.getTipoPredio().name()),
                new Dato("Presentada el:", solicitud.getFechaEnvio().format(FORMATO_HORA)),
                new Dato("Resuelta el:", solicitud.getFechaRevision() == null ? null
                        : solicitud.getFechaRevision().format(FORMATO_HORA))));
        List<List<String>> predio = predio(solicitud.getCodigoPredio());
        if (!predio.isEmpty()) {
            documento.seccion("Predio").tabla(List.of("Código", "Ubicación", "Uso", "Zona", "Área (m²)"), predio,
                    new float[] {1.1f, 3.2f, 1.1f, 1.2f, 0.9f}, new boolean[] {false, false, false, false, true});
        }
        BigDecimal deuda = solicitud.getDeudaPredio() == null ? BigDecimal.ZERO : solicitud.getDeudaPredio();
        documento.seccion("Resolución")
                .destacado(aceptada ? "SE DECLARA PROCEDENTE la solicitud." : "SE DECLARA OBSERVADA la solicitud.")
                .parrafo(aceptada
                        ? "Revisados los documentos presentados, la Gerencia de Rentas declara procedente la solicitud "
                                + "de " + tramite + " y actualiza el registro del predio en el padrón de contribuyentes "
                                + "de la Municipalidad Distrital de San Miguel."
                        : "Revisados los documentos presentados, la solicitud no cumple con los requisitos por el motivo "
                                + "siguiente: " + solicitud.getMotivoRechazo() + " Puede presentar una nueva solicitud "
                                + "con los documentos subsanados.")
                .datos(List.of(new Dato("Deuda del predio:", deuda.signum() == 0 ? "Sin deuda tributaria pendiente"
                        : "S/ " + String.format("%,.2f", deuda) + " pendiente; se mantiene en la cuenta del titular")))
                .seccion("Base legal")
                .parrafo("Artículo 14 del Texto Único Ordenado de la Ley de Tributación Municipal (D. S. N.° 156-2004-EF) "
                        + "y Texto Único Ordenado de la Ley N.° 27444, Ley del Procedimiento Administrativo General.")
                .firma(solicitud.getRevisor() == null ? "Gerencia de Rentas" : solicitud.getRevisor().getNombreCompleto(),
                        "Unidad de Registro Tributario", "Documento emitido por la Gerencia de Rentas. Código de "
                                + "verificación: " + codigo + ".");
        return documento.generar();
    }

    private Contribuyente contribuyente(String documento) {
        try {
            return samnet.buscarContribuyente(documento).stream().findFirst().orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<List<String>> predio(String codigoPredio) {
        if (codigoPredio == null) {
            return List.of();
        }
        try {
            List<List<String>> filas = new ArrayList<>();
            for (Predio p : samnet.buscarPredio(codigoPredio, null)) {
                filas.add(List.of(p.getCodigoPredio(), p.getUbicacion(), p.getUsoPredio() == null ? "—" : p.getUsoPredio(),
                        p.getZona() == null ? "—" : p.getZona(),
                        p.getAreaTerreno() == null ? "—" : String.format("%,.2f", p.getAreaTerreno())));
            }
            return filas;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
