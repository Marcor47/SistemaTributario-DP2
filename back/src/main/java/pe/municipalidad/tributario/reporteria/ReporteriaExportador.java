package pe.municipalidad.tributario.reporteria;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.reporteria.ExportarVistaPeticion.TablaExportada;

/* Excel y PDF de la vista en pantalla (RF-008). */
@Component
public class ReporteriaExportador {

    private static final String TITULO = "MUNICIPALIDAD DISTRITAL DE SAN MIGUEL";
    private static final String SUBTITULO = "Gerencia de Rentas - Plataforma analítica";
    private static final String PREFIJO_PNG = "data:image/png;base64,";
    private static final int FILAS_POR_TABLA_PDF = 60;

    public byte[] excel(ExportarVistaPeticion v, String usuario, String fecha) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle titulo = libro.createCellStyle();
            titulo.setFont(negrita);
            CellStyle cabecera = libro.createCellStyle();
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle numero = libro.createCellStyle();
            numero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            Sheet hoja = libro.createSheet("Vista");
            int fila = 0;
            fila = textos(hoja, fila, titulo, List.of(TITULO));
            fila = textos(hoja, fila, null, List.of("Reporte", v.reporte()));
            fila = textos(hoja, fila, null, List.of("Fecha de corte", v.corte()));
            fila = textos(hoja, fila, null, List.of("Filtros", v.filtros()));
            if (v.comparado() != null) {
                fila = textos(hoja, fila, null, List.of("Comparado con", v.comparado()));
            }
            if (v.resaltado() != null) {
                fila = textos(hoja, fila, null, List.of("Resaltado", v.resaltado()));
            }
            fila = textos(hoja, fila, null, List.of("Generado por", usuario + " - " + fecha));
            fila++;
            fila = textos(hoja, fila, titulo, List.of("INDICADORES"));
            fila = textos(hoja, fila, cabecera, List.of("Indicador", "Valor", "Detalle"));
            for (List<String> indicador : v.indicadores()) {
                fila = textos(hoja, fila, null, indicador);
            }
            for (TablaExportada grafico : v.graficos()) {
                fila = tabla(hoja, fila + 1, grafico, titulo, cabecera, numero);
            }
            if (v.ranking() != null && !v.ranking().encabezados().isEmpty()) {
                fila = tabla(hoja, fila + 1, v.ranking(), titulo, cabecera, numero);
                tabla(libro.createSheet("Ranking"), 0, v.ranking(), titulo, cabecera, numero);
            }
            for (int columna = 0; columna < 6; columna++) {
                hoja.setColumnWidth(columna, columna == 0 ? 11000 : 5200);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel de la vista", ex);
        }
    }

    private static int textos(Sheet hoja, int fila, CellStyle estilo, List<String> valores) {
        Row row = hoja.createRow(fila);
        for (int i = 0; i < valores.size(); i++) {
            Cell celda = row.createCell(i);
            celda.setCellValue(valores.get(i));
            if (estilo != null) {
                celda.setCellStyle(estilo);
            }
        }
        return fila + 1;
    }

    private static int tabla(Sheet hoja, int inicio, TablaExportada t, CellStyle titulo, CellStyle cabecera, CellStyle numero) {
        int fila = textos(hoja, inicio, titulo, List.of(t.titulo() + (t.tipo() == null ? "" : " (" + t.tipo() + ")")));
        fila = textos(hoja, fila, cabecera, t.encabezados());
        for (List<Object> valores : t.filas()) {
            Row row = hoja.createRow(fila++);
            for (int i = 0; i < valores.size(); i++) {
                Object valor = valores.get(i);
                Cell celda = row.createCell(i);
                if (valor instanceof Number n) {
                    celda.setCellValue(n.doubleValue());
                    celda.setCellStyle(numero);
                } else if (valor != null) {
                    celda.setCellValue(String.valueOf(valor));
                }
            }
        }
        return fila;
    }

    public byte[] pdf(ExportarVistaPeticion v, String usuario, String fecha) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Lienzo lienzo = new Lienzo(documento);
            try {
                lienzo.texto(TITULO, lienzo.negrita, 13);
                lienzo.texto(SUBTITULO + " - " + v.reporte(), lienzo.negrita, 11);
                lienzo.texto("Fecha de corte: " + v.corte() + " - Filtros: " + v.filtros(), lienzo.regular, 9);
                if (v.comparado() != null) {
                    lienzo.texto("Comparado con: " + v.comparado(), lienzo.regular, 9);
                }
                if (v.resaltado() != null) {
                    lienzo.texto("Resaltado: " + v.resaltado(), lienzo.regular, 9);
                }
                lienzo.texto("Generado por " + usuario + " - " + fecha, lienzo.regular, 9);
                lienzo.espacio(8);
                lienzo.texto("Indicadores", lienzo.negrita, 11);
                lienzo.tabla(List.of("Indicador", "Valor", "Detalle"), v.indicadores().stream()
                        .map(fila -> new ArrayList<Object>(fila)).map(fila -> (List<Object>) fila).toList());
                for (TablaExportada grafico : v.graficos()) {
                    lienzo.espacio(10);
                    boolean conImagen = grafico.imagen() != null && !grafico.imagen().isBlank();
                    lienzo.asegurar(conImagen ? Lienzo.ALTO_IMAGEN + 40 : 40);
                    lienzo.texto(grafico.titulo(), lienzo.negrita, 11);
                    lienzo.imagen(grafico.imagen());
                    lienzo.tabla(grafico.encabezados(), grafico.filas());
                }
                if (v.ranking() != null && !v.ranking().encabezados().isEmpty()) {
                    lienzo.espacio(10);
                    lienzo.asegurar(40);
                    lienzo.texto(v.ranking().titulo(), lienzo.negrita, 11);
                    lienzo.tabla(v.ranking().encabezados(), v.ranking().filas());
                }
            } finally {
                lienzo.cerrar();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la vista", ex);
        }
    }

    /* Escribe de arriba abajo en hojas A4 horizontales y agrega una hoja nueva cuando se acaba el espacio. */
    private static final class Lienzo {
        private static final float MARGEN = 36;
        private static final float TAMANO_TABLA = 8;
        private static final float ALTO_FILA = 13;
        private static final float ALTO_IMAGEN = 230;

        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument documento;
        private final float ancho = PDRectangle.A4.getHeight() - 2 * MARGEN;
        private PDPageContentStream hoja;
        private float y;

        Lienzo(PDDocument documento) throws IOException {
            this.documento = documento;
            nuevaHoja();
        }

        private void nuevaHoja() throws IOException {
            cerrar();
            PDPage pagina = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            documento.addPage(pagina);
            hoja = new PDPageContentStream(documento, pagina);
            y = pagina.getMediaBox().getHeight() - MARGEN;
        }

        void cerrar() throws IOException {
            if (hoja != null) {
                hoja.close();
                hoja = null;
            }
        }

        void espacio(float alto) {
            y -= alto;
        }

        void asegurar(float alto) throws IOException {
            if (y - alto < MARGEN) {
                nuevaHoja();
            }
        }

        void texto(String valor, PDType1Font fuente, float tamano) throws IOException {
            asegurar(tamano + 4);
            y -= tamano + 3;
            escribir(recortar(limpiar(valor), fuente, tamano, ancho), fuente, tamano, MARGEN, y);
        }

        private void escribir(String valor, PDType1Font fuente, float tamano, float x, float linea) throws IOException {
            hoja.beginText();
            hoja.setFont(fuente, tamano);
            hoja.newLineAtOffset(x, linea);
            hoja.showText(valor);
            hoja.endText();
        }

        void imagen(String dato) throws IOException {
            if (dato == null || dato.isBlank()) {
                return;
            }
            if (!dato.startsWith(PREFIJO_PNG)) {
                throw new DatoInvalidoException("IMAGEN_INVALIDA", "La imagen del gráfico no es válida.");
            }
            PDImageXObject imagen;
            try {
                imagen = PDImageXObject.createFromByteArray(documento,
                        Base64.getDecoder().decode(dato.substring(PREFIJO_PNG.length())), "grafico.png");
            } catch (IllegalArgumentException | IOException ex) {
                throw new DatoInvalidoException("IMAGEN_INVALIDA", "La imagen del gráfico no es válida.");
            }
            float escala = Math.min(ancho * 0.7f / imagen.getWidth(), ALTO_IMAGEN / imagen.getHeight());
            float w = imagen.getWidth() * escala;
            float h = imagen.getHeight() * escala;
            asegurar(h + 6);
            y -= h + 4;
            hoja.drawImage(imagen, MARGEN, y, w, h);
        }

        void tabla(List<String> encabezados, List<List<Object>> filas) throws IOException {
            if (encabezados.isEmpty()) {
                return;
            }
            float columna = ancho / encabezados.size();
            fila(encabezados.stream().map(Object.class::cast).toList(), negrita, columna);
            int escritas = 0;
            for (List<Object> valores : filas) {
                if (escritas++ == FILAS_POR_TABLA_PDF) {
                    fila(List.of("... (" + (filas.size() - FILAS_POR_TABLA_PDF) + " filas más en el Excel)"), regular, ancho);
                    break;
                }
                if (y - ALTO_FILA < MARGEN) {
                    nuevaHoja();
                    fila(encabezados.stream().map(Object.class::cast).toList(), negrita, columna);
                }
                fila(valores, regular, columna);
            }
        }

        private void fila(List<Object> valores, PDType1Font fuente, float columna) throws IOException {
            asegurar(ALTO_FILA);
            y -= ALTO_FILA;
            for (int i = 0; i < valores.size(); i++) {
                Object valor = valores.get(i);
                String texto = valor instanceof Number n ? numero(n) : valor == null ? "-" : String.valueOf(valor);
                escribir(recortar(limpiar(texto), fuente, TAMANO_TABLA, columna - 6), fuente, TAMANO_TABLA,
                        MARGEN + i * columna, y);
            }
        }

        private static String numero(Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.format(Locale.US, "%,d", (long) d)
                    : String.format(Locale.US, "%,.2f", d);
        }

        private static String recortar(String texto, PDType1Font fuente, float tamano, float maximo) throws IOException {
            String actual = texto;
            while (!actual.isEmpty() && fuente.getStringWidth(actual) / 1000 * tamano > maximo) {
                actual = actual.substring(0, actual.length() - 1);
            }
            return actual.length() == texto.length() ? texto : actual.isEmpty() ? "" : actual.substring(0, Math.max(0, actual.length() - 2)) + "..";
        }

        /* Las fuentes estándar de PDF solo admiten WinAnsi. */
        private static String limpiar(String texto) {
            StringBuilder salida = new StringBuilder(texto.length());
            texto.codePoints().forEach(c -> {
                if (c == 0x2212 || c == 0x2014 || c == 0x2013 || c == 0xB7) {
                    salida.append('-');
                } else if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255)) {
                    salida.appendCodePoint(c == 160 ? ' ' : c);
                } else if (Character.isWhitespace(c)) {
                    salida.append(' ');
                }
            });
            return salida.toString();
        }
    }
}
