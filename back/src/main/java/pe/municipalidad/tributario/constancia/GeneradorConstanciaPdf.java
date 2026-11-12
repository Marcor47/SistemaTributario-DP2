package pe.municipalidad.tributario.constancia;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * PDF de la Constancia de No Adeudo que emite el operador (HU020) y ventanilla (HU042). Reproduce el formato
 * institucional de la constancia de referencia del buzón: banda superior azul, título destacado, datos del
 * contribuyente, cuerpo con predios y período, y pie numerado. Si se firma con el firmador simulado lleva la
 * marca "DOCUMENTO DE PRUEBA" (RNF-015). El contenido se envuelve por ancho y continúa en otra hoja si hace falta.
 */
@Component
public class GeneradorConstanciaPdf {

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float ALTO_PAGINA = PDRectangle.A4.getHeight();
    private static final float ANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final float MARGEN = 44f;
    private static final float ANCHO_CONTENIDO = ANCHO_PAGINA - (2 * MARGEN);
    private static final float ALTO_BANDA = 110f;
    private static final float LIMITE_INFERIOR = 90f;
    private static final float INTERLINEADO_CUERPO = 19f;
    private static final float ESPACIO_PARRAFO = 8f;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Color AZUL = new Color(0.090196f, 0.25098f, 0.419608f);
    private static final Color NARANJA = new Color(0.607843f, 0.290196f, 0f);
    private static final Color TEXTO = new Color(0.14902f, 0.243137f, 0.329412f);
    private static final Color REGLA = new Color(0.839216f, 0.886275f, 0.937255f);
    private static final Color PIE = new Color(0.321569f, 0.392157f, 0.466667f);

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            boolean dePrueba) {
        return generar(numeroConstancia, contribuyente, fechaEmision, null, null, null, null, dePrueba);
    }

    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            String prediosIncluidos, String rangoFechas, String trabajadorEmisor, boolean dePrueba) {
        return generar(numeroConstancia, contribuyente, fechaEmision, null, prediosIncluidos, rangoFechas,
                trabajadorEmisor, dePrueba);
    }

    /*
     * HU042: la constancia de ventanilla cita la fecha de presentación de la solicitud, que es cuando se evaluó la
     * deuda. La emisión puede ser posterior sin que eso cambie la condición del trámite.
     */
    public byte[] generar(String numeroConstancia, Contribuyente contribuyente, LocalDateTime fechaEmision,
            LocalDate fechaPresentacion, String prediosIncluidos, String rangoFechas, String trabajadorEmisor,
            boolean dePrueba) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Lienzo lienzo = new Lienzo(pdf, numeroConstancia, dePrueba);
            lienzo.portada(contribuyente, fechaEmision, fechaPresentacion, prediosIncluidos, rangoFechas,
                    trabajadorEmisor);
            lienzo.cerrar();
            lienzo.pies();
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la constancia " + numeroConstancia, ex);
        }
    }

    /* Dibuja de arriba abajo y agrega una hoja nueva, con la misma banda, cuando se acaba el espacio. */
    private static final class Lienzo {

        private final PDDocument pdf;
        private final String numero;
        private final boolean dePrueba;
        private PDPageContentStream contenido;
        private float y;

        Lienzo(PDDocument pdf, String numero, boolean dePrueba) throws IOException {
            this.pdf = pdf;
            this.numero = textoPdf(numero);
            this.dePrueba = dePrueba;
            nuevaPagina(false);
        }

        void portada(Contribuyente contribuyente, LocalDateTime fechaEmision, LocalDate fechaPresentacion,
                String prediosIncluidos, String rangoFechas, String trabajadorEmisor) throws IOException {
            texto(MARGEN, ALTO_PAGINA - 160, dePrueba ? "DOCUMENTO DE PRUEBA" : "CONSTANCIA DE NO ADEUDO",
                    NEGRITA, 23, NARANJA);
            texto(MARGEN, ALTO_PAGINA - 200,
                    dePrueba ? "Constancia de No Adeudo" : "No registra deuda tributaria pendiente",
                    NEGRITA, 17, AZUL);

            y = ALTO_PAGINA - 233;
            detalle("Referencia: ", numero);
            detalle("Contribuyente: ", contribuyente.getNombreRazonSocial());
            detalle("Documento: ", contribuyente.getTipoDocumento().name() + " " + contribuyente.getNumeroDocumento());
            detalle("Código de contribuyente: ", contribuyente.getCodigoContribuyente());
            detalle("Fecha de emisión: ", fechaEmision.format(FORMATO_FECHA));

            float lineaRegla = y - 1;
            regla(lineaRegla);
            y = lineaRegla - 38;

            String declaracion = "Se deja constancia de que el contribuyente no registra deuda tributaria pendiente "
                    + "con la Municipalidad Distrital de San Miguel";
            if (fechaPresentacion != null) {
                declaracion += " a la fecha de presentación de la solicitud ("
                        + fechaPresentacion.format(FORMATO_FECHA) + ").";
            } else {
                declaracion += " a la fecha de emisión.";
            }
            parrafo(declaracion, REGULAR, 12, TEXTO);
            if (prediosIncluidos != null && !prediosIncluidos.isBlank()) {
                parrafo("Predios comprendidos: " + prediosIncluidos.strip(), REGULAR, 12, TEXTO);
            }
            if (rangoFechas != null && !rangoFechas.isBlank()) {
                parrafo("Período cubierto: " + rangoFechas.strip(), REGULAR, 12, TEXTO);
            }
            if (trabajadorEmisor != null && !trabajadorEmisor.isBlank()) {
                parrafo("Atendido en ventanilla por: " + trabajadorEmisor.strip(), REGULAR, 12, TEXTO);
            }
            if (dePrueba) {
                parrafo("Documento de prueba. Firma municipal simulada: este documento no tiene validez legal.",
                        NEGRITA, 12, TEXTO);
            }
        }

        void cerrar() throws IOException {
            if (contenido != null) {
                contenido.close();
                contenido = null;
            }
        }

        /* El pie se escribe al final para conocer el total de hojas. */
        void pies() throws IOException {
            int total = pdf.getNumberOfPages();
            for (int indice = 0; indice < total; indice++) {
                try (PDPageContentStream pie = new PDPageContentStream(pdf, pdf.getPage(indice), AppendMode.APPEND,
                        true, true)) {
                    pie.setNonStrokingColor(PIE);
                    pie.setFont(REGULAR, 10);
                    pie.beginText();
                    pie.newLineAtOffset(MARGEN, 58);
                    pie.showText(dePrueba
                            ? "DOCUMENTO DE PRUEBA - Sin validez legal"
                            : "Constancia de No Adeudo - Municipalidad Distrital de San Miguel");
                    pie.endText();

                    String numeroPagina = (indice + 1) + " / " + total;
                    float ancho = REGULAR.getStringWidth(numeroPagina) / 1000f * 10f;
                    pie.beginText();
                    pie.newLineAtOffset(MARGEN + ANCHO_CONTENIDO - ancho, 36);
                    pie.showText(numeroPagina);
                    pie.endText();
                }
            }
        }

        private void nuevaPagina(boolean continuacion) throws IOException {
            cerrar();
            PDPage pagina = new PDPage(PDRectangle.A4);
            pdf.addPage(pagina);
            contenido = new PDPageContentStream(pdf, pagina);
            banda(continuacion);
            y = continuacion ? ALTO_PAGINA - 150 : ALTO_PAGINA - 233;
        }

        private void banda(boolean continuacion) throws IOException {
            contenido.setNonStrokingColor(AZUL);
            contenido.addRect(0, ALTO_PAGINA - ALTO_BANDA, ANCHO_PAGINA, ALTO_BANDA);
            contenido.fill();
            texto(MARGEN, ALTO_PAGINA - 48, "Municipalidad de San Miguel", NEGRITA, 20, Color.WHITE);
            texto(MARGEN, ALTO_PAGINA - 75,
                    continuacion
                            ? "Constancia de No Adeudo - Referencia: " + numero
                            : "Gerencia de Rentas - Servicios en línea",
                    REGULAR, continuacion ? 11 : 12, Color.WHITE);
        }

        private void detalle(String etiqueta, String valor) throws IOException {
            String texto = etiqueta + (valor == null ? "" : valor);
            for (String linea : lineas(texto, REGULAR, 11, ANCHO_CONTENIDO)) {
                asegurar(18);
                texto(MARGEN, y, linea, REGULAR, 11, Color.BLACK);
                y -= 18;
            }
        }

        private void parrafo(String valor, PDFont fuente, float tamano, Color color) throws IOException {
            for (String linea : lineas(valor, fuente, tamano, ANCHO_CONTENIDO)) {
                asegurar(INTERLINEADO_CUERPO);
                texto(MARGEN, y, linea, fuente, tamano, color);
                y -= INTERLINEADO_CUERPO;
            }
            y -= ESPACIO_PARRAFO;
        }

        private void asegurar(float alto) throws IOException {
            if (y - alto < LIMITE_INFERIOR) {
                nuevaPagina(true);
            }
        }

        private void regla(float linea) throws IOException {
            contenido.setStrokingColor(REGLA);
            contenido.setLineWidth(0.8f);
            contenido.moveTo(MARGEN, linea);
            contenido.lineTo(MARGEN + ANCHO_CONTENIDO, linea);
            contenido.stroke();
        }

        private void texto(float x, float linea, String valor, PDFont fuente, float tamano, Color color)
                throws IOException {
            contenido.beginText();
            contenido.setNonStrokingColor(color);
            contenido.setFont(fuente, tamano);
            contenido.newLineAtOffset(x, linea);
            contenido.showText(valor);
            contenido.endText();
        }
    }

    /* Divide el texto en líneas que caben en el ancho y sanea lo que la fuente WinAnsi no puede representar. */
    private static List<String> lineas(String valor, PDFont fuente, float tamano, float anchoMax) throws IOException {
        List<String> resultado = new ArrayList<>();
        for (String bloque : textoPdf(valor).split("\n", -1)) {
            String actual = "";
            for (String palabra : bloque.split(" ", -1)) {
                if (palabra.isEmpty()) {
                    continue;
                }
                String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (ancho(candidato, fuente, tamano) <= anchoMax) {
                    actual = candidato;
                    continue;
                }
                if (!actual.isEmpty()) {
                    resultado.add(actual);
                    actual = "";
                }
                String resto = palabra;
                while (ancho(resto, fuente, tamano) > anchoMax) {
                    int corte = resto.length();
                    while (corte > 1 && ancho(resto.substring(0, corte), fuente, tamano) > anchoMax) {
                        corte--;
                    }
                    resultado.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                actual = resto;
            }
            if (!actual.isEmpty()) {
                resultado.add(actual);
            }
        }
        return resultado;
    }

    private static float ancho(String valor, PDFont fuente, float tamano) throws IOException {
        return fuente.getStringWidth(valor) / 1000f * tamano;
    }

    private static String textoPdf(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder(valor.length());
        for (char caracter : valor.toCharArray()) {
            switch (caracter) {
                case '\n' -> texto.append('\n');
                case '\u2013', '\u2014' -> texto.append('-');
                case '\u2018', '\u2019' -> texto.append('\'');
                case '\u201C', '\u201D' -> texto.append('"');
                case '\u2026' -> texto.append("...");
                default -> {
                    if (caracter >= 32 && caracter <= 255 && !(caracter >= 127 && caracter <= 159)) {
                        texto.append(caracter);
                    } else {
                        texto.append(' ');
                    }
                }
            }
        }
        return texto.toString();
    }
}
