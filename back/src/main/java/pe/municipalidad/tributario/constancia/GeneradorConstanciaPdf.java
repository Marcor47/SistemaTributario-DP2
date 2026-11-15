package pe.municipalidad.tributario.constancia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.documentos.DocumentoInstitucionalPdf;
import pe.municipalidad.tributario.documentos.DocumentoInstitucionalPdf.Dato;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * PDF de la Constancia de No Adeudo (HU020, HU024 y HU042) con la plantilla institucional: datos completos del
 * contribuyente y de sus predios, código único no predecible (V52), fecha de emisión, estado de la deuda, base legal,
 * vigencia y bloque de firma digital de la Municipalidad.
 */
@Component
public class GeneradorConstanciaPdf {

    private static final Logger LOG = LoggerFactory.getLogger(GeneradorConstanciaPdf.class);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int DIAS_VIGENCIA = 30;
    private static final String AREA = "Gerencia de Rentas";

    private final Optional<SamnetGateway> samnet;

    public GeneradorConstanciaPdf() {
        this.samnet = Optional.empty();
    }

    @Autowired
    public GeneradorConstanciaPdf(SamnetGateway samnet) {
        this.samnet = Optional.ofNullable(samnet);
    }

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            boolean firmaLocal) {
        return generar(numeroConstancia, contribuyente, fechaEmision, null, null, null, null, firmaLocal);
    }

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            String prediosIncluidos, String rangoFechas, String trabajadorEmisor, boolean firmaLocal) {
        return generar(numeroConstancia, contribuyente, fechaEmision, null, prediosIncluidos, rangoFechas,
                trabajadorEmisor, firmaLocal);
    }

    /*
     * HU042: la constancia de ventanilla cita la fecha de presentación de la solicitud, que es cuando se evaluó la
     * deuda. La emisión puede ser posterior sin que eso cambie la condición del trámite.
     */
    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            LocalDate fechaPresentacion, String prediosIncluidos, String rangoFechas, String trabajadorEmisor,
            boolean firmaLocal) {
        LocalDate fechaEvaluacion = fechaPresentacion != null ? fechaPresentacion : fechaEmision.toLocalDate();
        DocumentoInstitucionalPdf documento = new DocumentoInstitucionalPdf(AREA, "Constancia de No Adeudo",
                numeroConstancia, fechaEmision);

        documento.seccion("Datos del contribuyente").datos(List.of(
                new Dato("Contribuyente:", contribuyente.getNombreRazonSocial()),
                new Dato("Documento:", contribuyente.getTipoDocumento().name() + " " + contribuyente.getNumeroDocumento()),
                new Dato("Código de contribuyente:", contribuyente.getCodigoContribuyente()),
                new Dato("Domicilio fiscal:", contribuyente.getDomicilioFiscal() == null ? null
                        : contribuyente.getDomicilioFiscal().direccion()),
                new Dato("Condición:", contribuyente.getCondicion())));

        documento.seccion("Constancia")
                .parrafo("La Gerencia de Rentas de la Municipalidad Distrital de San Miguel HACE CONSTAR que el "
                        + "contribuyente identificado en el presente documento NO REGISTRA DEUDA TRIBUTARIA PENDIENTE "
                        + "por Impuesto Predial ni por Arbitrios Municipales, incluido el interés moratorio, a la fecha "
                        + (fechaPresentacion != null ? "de presentación de su solicitud (" : "de emisión (")
                        + fechaEvaluacion.format(FORMATO_FECHA) + "), según la información registrada en el sistema "
                        + "de gestión tributaria de la Municipalidad.")
                .datos(List.of(
                        new Dato("Estado de la deuda:", "Sin deuda tributaria pendiente (S/ 0.00)"),
                        new Dato("Período cubierto:", rangoFechas == null || rangoFechas.isBlank()
                                ? "Hasta la fecha de emisión" : rangoFechas.strip()),
                        new Dato("Predios comprendidos:", prediosIncluidos == null || prediosIncluidos.isBlank()
                                ? "Todos los predios del contribuyente" : prediosIncluidos.strip()),
                        new Dato("Fecha y hora de emisión:", fechaEmision.format(FORMATO_HORA)),
                        new Dato("Vigencia:", DIAS_VIGENCIA + " días calendario, hasta el "
                                + fechaEmision.toLocalDate().plusDays(DIAS_VIGENCIA).format(FORMATO_FECHA)),
                        new Dato("Atendido por:", trabajadorEmisor == null || trabajadorEmisor.isBlank()
                                ? "Servicios en Línea" : trabajadorEmisor.strip())));

        List<List<String>> filas = predios(contribuyente.getCodigoContribuyente());
        if (!filas.isEmpty()) {
            documento.seccion("Predios registrados del contribuyente")
                    .tabla(List.of("Código", "Ubicación", "Uso", "Zona"), filas, new float[] {1.1f, 3.4f, 1.1f, 1.2f},
                            new boolean[] {false, false, false, false});
        }

        documento.seccion("Base legal")
                .parrafo("Texto Único Ordenado de la Ley de Tributación Municipal, aprobado por Decreto Supremo N.° "
                        + "156-2004-EF; Texto Único Ordenado del Código Tributario, aprobado por Decreto Supremo N.° "
                        + "133-2013-EF; y Ley N.° 27269, Ley de Firmas y Certificados Digitales.")
                .nota("La constancia no acredita la inexistencia de obligaciones que se determinen con posterioridad "
                        + "por fiscalización. Su autenticidad puede verificarse en la Gerencia de Rentas con el código "
                        + numeroConstancia + ".")
                .firma("Gerencia de Rentas", "Municipalidad Distrital de San Miguel",
                        "Documento firmado digitalmente por la Municipalidad Distrital de San Miguel. Código de "
                                + "verificación: " + numeroConstancia + ".");
        return documento.generar();
    }

    /* Predios vigentes del contribuyente; si SAMNET no responde, la constancia se emite sin el detalle. */
    private List<List<String>> predios(String codigoContribuyente) {
        if (samnet.isEmpty()) {
            return List.of();
        }
        try {
            List<List<String>> filas = new ArrayList<>();
            for (Predio predio : samnet.get().buscarPredio(null, codigoContribuyente)) {
                if (predio.getFechaBaja() == null) {
                    filas.add(List.of(predio.getCodigoPredio(), predio.getUbicacion(),
                            predio.getUsoPredio() == null ? "—" : predio.getUsoPredio(),
                            predio.getZona() == null ? "—" : predio.getZona()));
                }
            }
            return filas;
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo obtener los predios del contribuyente para la constancia: {}", ex.getMessage());
            return List.of();
        }
    }
}
