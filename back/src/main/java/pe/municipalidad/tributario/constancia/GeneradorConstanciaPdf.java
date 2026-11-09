package pe.municipalidad.tributario.constancia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * PDF de la Constancia de No Adeudo que emite el operador (HU020). Si se firma con el firmador simulado lleva la
 * marca "DOCUMENTO DE PRUEBA" (RNF-015). El formato oficial lo reemplazará cuando la Municipalidad entregue la
 * plantilla (RF-046).
 */
@Component
public class GeneradorConstanciaPdf {

    private static final float TAMANO_LETRA = 12;
    private static final float MARGEN_IZQUIERDO = 55;
    private static final float INICIO_VERTICAL = 740;
    private static final float INTERLINEADO = 22;

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            boolean dePrueba) {
        return generar(numeroConstancia, contribuyente, fechaEmision, null, null, null, dePrueba);
    }

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            String prediosIncluidos, String rangoFechas, String trabajadorEmisor, boolean dePrueba) {
        List<String> lineas = new ArrayList<>();
        if (dePrueba) {
            lineas.add("DOCUMENTO DE PRUEBA - SIN VALIDEZ LEGAL");
        }
        lineas.addAll(List.of(
                "MUNICIPALIDAD DISTRITAL DE SAN MIGUEL - GERENCIA DE RENTAS",
                "CONSTANCIA DE NO ADEUDO",
                "Número: " + numeroConstancia,
                "Contribuyente: " + contribuyente.getNombreRazonSocial(),
                "Documento: " + contribuyente.getTipoDocumento().name() + " " + contribuyente.getNumeroDocumento(),
                "Código de contribuyente: " + contribuyente.getCodigoContribuyente(),
                "Fecha de emisión: " + fechaEmision.toLocalDate(),
                "Se deja constancia de que el contribuyente no registra deuda tributaria pendiente",
                "con la Municipalidad a la fecha de emisión."));

        if (prediosIncluidos != null && !prediosIncluidos.isBlank()) {
            lineas.add("Predios comprendidos: " + prediosIncluidos);
        }
        if (rangoFechas != null && !rangoFechas.isBlank()) {
            lineas.add("Período cubierto: " + rangoFechas);
        }
        if (trabajadorEmisor != null && !trabajadorEmisor.isBlank()) {
            lineas.add("Atendido en Ventanilla por: " + trabajadorEmisor);
        }

        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), TAMANO_LETRA);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, INICIO_VERTICAL);
                contenido.setLeading(INTERLINEADO);
                for (String linea : lineas) {
                    contenido.showText(linea);
                    contenido.newLine();
                }
                contenido.endText();
            }
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la constancia " + numeroConstancia, ex);
        }
    }
}
