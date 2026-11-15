package pe.municipalidad.tributario.documentos;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;

/*
 * Plantilla institucional única de los documentos que emite el sistema (constancias, resoluciones de trámites de
 * predio, resúmenes y reportes): logo y nombre de la Municipalidad Distrital de San Miguel en el encabezado, área
 * emisora, título, código, lugar y fecha, cuerpo con márgenes y tipografía uniformes, bloque de firma y sello, y pie
 * con la dirección, el portal web y la numeración de páginas. Se arma por bloques y salta de hoja cuando se acaba el
 * espacio, repitiendo encabezado y pie.
 */
public final class DocumentoInstitucionalPdf {

    public static final String ENTIDAD = "MUNICIPALIDAD DISTRITAL DE SAN MIGUEL";
    public static final String DIRECCION = "Av. Federico Gallese N.° 370, San Miguel, Lima - Perú";
    public static final String PORTAL = "www.munisanmiguel.gob.pe";

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont CURSIVA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final float ALTO = PDRectangle.A4.getHeight();
    private static final float ANCHO = PDRectangle.A4.getWidth();
    private static final float MARGEN = 56f;
    private static final float ANCHO_UTIL = ANCHO - 2 * MARGEN;
    private static final float INICIO_CUERPO = ALTO - 128f;
    private static final float FIN_CUERPO = 92f;
    private static final float CUERPO = 10.5f;
    private static final float INTERLINEA = 15f;

    private static final Color AZUL = new Color(23, 64, 107);
    private static final Color CELESTE = new Color(34, 196, 240);
    private static final Color TEXTO = new Color(33, 41, 52);
    private static final Color SECUNDARIO = new Color(82, 100, 119);
    private static final Color REGLA = new Color(214, 226, 239);
    private static final Color FONDO_TABLA = new Color(240, 245, 251);

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    public record Dato(String etiqueta, String valor) {
    }

    private sealed interface Bloque permits Seccion, Parrafo, Datos, Tabla, Firma, Nota, Espacio {
    }

    private record Seccion(String titulo) implements Bloque {
    }

    private record Parrafo(String texto, boolean destacado) implements Bloque {
    }

    private record Datos(List<Dato> datos) implements Bloque {
    }

    private record Tabla(List<String> encabezados, List<List<String>> filas, float[] proporciones, boolean[] numericas)
            implements Bloque {
    }

    private record Firma(String nombre, String cargo, String sello) implements Bloque {
    }

    private record Nota(String texto) implements Bloque {
    }

    private record Espacio(float alto) implements Bloque {
    }

    private final String area;
    private final String titulo;
    private final String codigo;
    private final LocalDateTime fecha;
    private final List<Bloque> bloques = new ArrayList<>();

    public DocumentoInstitucionalPdf(String area, String titulo, String codigo, LocalDateTime fecha) {
        this.area = area;
        this.titulo = titulo;
        this.codigo = codigo;
        this.fecha = fecha;
    }

    public static String fechaLarga(LocalDateTime fecha) {
        return fecha.format(FECHA_LARGA);
    }

    public DocumentoInstitucionalPdf seccion(String texto) {
        bloques.add(new Seccion(texto));
        return this;
    }

    public DocumentoInstitucionalPdf parrafo(String texto) {
        if (texto != null && !texto.isBlank()) {
            bloques.add(new Parrafo(texto, false));
        }
        return this;
    }

    public DocumentoInstitucionalPdf destacado(String texto) {
        bloques.add(new Parrafo(texto, true));
        return this;
    }

    public DocumentoInstitucionalPdf datos(List<Dato> datos) {
        List<Dato> conValor = datos.stream().filter(d -> d.valor() != null && !d.valor().isBlank()).toList();
        if (!conValor.isEmpty()) {
            bloques.add(new Datos(conValor));
        }
        return this;
    }

    /* proporciones: ancho relativo de cada columna; numericas: columnas alineadas a la derecha. */
    public DocumentoInstitucionalPdf tabla(List<String> encabezados, List<List<String>> filas, float[] proporciones,
            boolean[] numericas) {
        bloques.add(new Tabla(encabezados, filas, proporciones, numericas));
        return this;
    }

    public DocumentoInstitucionalPdf firma(String nombre, String cargo, String sello) {
        bloques.add(new Firma(nombre, cargo, sello));
        return this;
    }

    public DocumentoInstitucionalPdf nota(String texto) {
        bloques.add(new Nota(texto));
        return this;
    }

    public DocumentoInstitucionalPdf espacio(float alto) {
        bloques.add(new Espacio(alto));
        return this;
    }

    public byte[] generar() {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Lienzo lienzo = new Lienzo(pdf);
            lienzo.portada();
            for (Bloque bloque : bloques) {
                lienzo.dibujar(bloque);
            }
            lienzo.cerrar();
            lienzo.pies();
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el documento " + codigo, ex);
        }
    }

    private final class Lienzo {

        private final PDDocument pdf;
        private final PDImageXObject logo;
        private PDPageContentStream contenido;
        private float y;

        Lienzo(PDDocument pdf) throws IOException {
            this.pdf = pdf;
            this.logo = cargarLogo(pdf);
            nuevaPagina();
        }

        void portada() throws IOException {
            List<String> lineasTitulo = lineas(titulo.toUpperCase(Locale.ROOT), NEGRITA, 16, ANCHO_UTIL);
            for (String linea : lineasTitulo) {
                centrado(linea, NEGRITA, 16, AZUL);
                y -= 21;
            }
            y -= 2;
            String referencia = "N.° " + codigo;
            centrado(referencia, NEGRITA, 11, CELESTE.darker());
            y -= 18;
            String lugar = fecha == null ? "San Miguel, ____ de ________________ de 20____"
                    : "San Miguel, " + fechaLarga(fecha);
            float ancho = ancho(lugar, CURSIVA, 10);
            texto(MARGEN + ANCHO_UTIL - ancho, y, lugar, CURSIVA, 10, SECUNDARIO);
            y -= 24;
        }

        void dibujar(Bloque bloque) throws IOException {
            switch (bloque) {
                case Seccion s -> seccion(s.titulo());
                case Parrafo p -> parrafo(p.texto(), p.destacado() ? NEGRITA : REGULAR, CUERPO, TEXTO, true);
                case Datos d -> datos(d.datos());
                case Tabla t -> tabla(t);
                case Firma f -> firma(f);
                case Nota n -> parrafo(n.texto(), CURSIVA, 8.5f, SECUNDARIO, false);
                case Espacio e -> y -= e.alto();
            }
        }

        private void seccion(String texto) throws IOException {
            asegurar(40);
            y -= 6;
            texto(MARGEN, y, texto.toUpperCase(Locale.ROOT), NEGRITA, 10.5f, AZUL);
            y -= 5;
            linea(MARGEN, y, MARGEN + ANCHO_UTIL, REGLA, 0.8f);
            y -= 15;
        }

        private void parrafo(String texto, PDFont fuente, float tamano, Color color, boolean justificado)
                throws IOException {
            float interlinea = tamano < CUERPO ? 12f : INTERLINEA;
            for (String bloque : limpiar(texto).split("\n")) {
                List<String> lineas = lineas(bloque, fuente, tamano, ANCHO_UTIL);
                for (int i = 0; i < lineas.size(); i++) {
                    asegurar(interlinea);
                    boolean ultima = i == lineas.size() - 1;
                    if (justificado && !ultima) {
                        justificada(lineas.get(i), fuente, tamano, color);
                    } else {
                        texto(MARGEN, y, lineas.get(i), fuente, tamano, color);
                    }
                    y -= interlinea;
                }
            }
            y -= 6;
        }

        private void datos(List<Dato> datos) throws IOException {
            float anchoEtiqueta = 150f;
            for (Dato dato : datos) {
                anchoEtiqueta = Math.max(anchoEtiqueta, ancho(dato.etiqueta(), NEGRITA, CUERPO) + 12);
            }
            anchoEtiqueta = Math.min(anchoEtiqueta, ANCHO_UTIL * 0.55f);
            for (Dato dato : datos) {
                List<String> valor = lineas(dato.valor(), REGULAR, CUERPO, ANCHO_UTIL - anchoEtiqueta);
                asegurar(INTERLINEA * Math.max(1, valor.size()));
                texto(MARGEN, y, limpiar(dato.etiqueta()), NEGRITA, CUERPO, SECUNDARIO);
                for (String linea : valor) {
                    texto(MARGEN + anchoEtiqueta, y, linea, REGULAR, CUERPO, TEXTO);
                    y -= INTERLINEA;
                }
            }
            y -= 6;
        }

        private void tabla(Tabla t) throws IOException {
            int columnas = t.encabezados().size();
            float total = 0;
            for (float p : t.proporciones()) {
                total += p;
            }
            float[] anchos = new float[columnas];
            for (int c = 0; c < columnas; c++) {
                anchos[c] = ANCHO_UTIL * t.proporciones()[c] / total;
            }
            float tamano = 9f;
            float alto = 17f;
            encabezadoTabla(t.encabezados(), anchos, t.numericas(), tamano, alto);
            boolean par = false;
            for (List<String> fila : t.filas()) {
                List<List<String>> celdas = new ArrayList<>();
                int maximo = 1;
                for (int c = 0; c < columnas; c++) {
                    List<String> lineas = lineas(c < fila.size() ? fila.get(c) : "", REGULAR, tamano, anchos[c] - 8);
                    celdas.add(lineas);
                    maximo = Math.max(maximo, lineas.size());
                }
                float altoFila = alto + (maximo - 1) * 11f;
                if (y - altoFila < FIN_CUERPO) {
                    nuevaPagina();
                    encabezadoTabla(t.encabezados(), anchos, t.numericas(), tamano, alto);
                }
                if (par) {
                    rectangulo(MARGEN, y - altoFila, ANCHO_UTIL, altoFila, FONDO_TABLA);
                }
                par = !par;
                float x = MARGEN;
                for (int c = 0; c < columnas; c++) {
                    float lineaY = y - 12;
                    for (String linea : celdas.get(c)) {
                        float posicion = t.numericas()[c] ? x + anchos[c] - 4 - ancho(linea, REGULAR, tamano) : x + 4;
                        texto(posicion, lineaY, linea, REGULAR, tamano, TEXTO);
                        lineaY -= 11f;
                    }
                    x += anchos[c];
                }
                y -= altoFila;
                linea(MARGEN, y, MARGEN + ANCHO_UTIL, REGLA, 0.5f);
            }
            if (t.filas().isEmpty()) {
                texto(MARGEN + 4, y - 12, "No hay registros para mostrar.", CURSIVA, tamano, SECUNDARIO);
                y -= alto;
            }
            y -= 12;
        }

        private void encabezadoTabla(List<String> encabezados, float[] anchos, boolean[] numericas, float tamano,
                float alto)
                throws IOException {
            asegurar(alto * 2);
            rectangulo(MARGEN, y - alto, ANCHO_UTIL, alto, AZUL);
            float x = MARGEN;
            for (int c = 0; c < encabezados.size(); c++) {
                String encabezado = recortar(encabezados.get(c), NEGRITA, tamano, anchos[c] - 8);
                float posicion = numericas[c] ? x + anchos[c] - 4 - ancho(encabezado, NEGRITA, tamano) : x + 4;
                texto(posicion, y - 12, encabezado, NEGRITA, tamano, Color.WHITE);
                x += anchos[c];
            }
            y -= alto;
        }

        private void firma(Firma f) throws IOException {
            asegurar(f.sello() == null || f.sello().isBlank() ? 95 : 125);
            y -= 40;
            float ancho = 220f;
            float x = MARGEN + (ANCHO_UTIL - ancho) / 2;
            linea(x, y, x + ancho, SECUNDARIO, 0.7f);
            y -= 13;
            centrado(f.nombre(), NEGRITA, 10, TEXTO);
            y -= 13;
            centrado(f.cargo(), REGULAR, 9.5f, SECUNDARIO);
            y -= 12;
            centrado(ENTIDAD, REGULAR, 8.5f, SECUNDARIO);
            if (f.sello() != null && !f.sello().isBlank()) {
                y -= 16;
                for (String linea : lineas(f.sello(), CURSIVA, 8.5f, ANCHO_UTIL)) {
                    centrado(linea, CURSIVA, 8.5f, SECUNDARIO);
                    y -= 11;
                }
            }
            y -= 14;
        }

        void cerrar() throws IOException {
            if (contenido != null) {
                contenido.close();
                contenido = null;
            }
        }

        void pies() throws IOException {
            int total = pdf.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream pie = new PDPageContentStream(pdf, pdf.getPage(i), AppendMode.APPEND, true,
                        true)) {
                    contenido = pie;
                    linea(MARGEN, 70, MARGEN + ANCHO_UTIL, CELESTE, 1.2f);
                    texto(MARGEN, 56, ENTIDAD + " - " + area, NEGRITA, 8, AZUL);
                    texto(MARGEN, 45, DIRECCION + "  |  " + PORTAL, REGULAR, 8, SECUNDARIO);
                    String pagina = "Página " + (i + 1) + " de " + total;
                    texto(MARGEN + ANCHO_UTIL - ancho(pagina, REGULAR, 8), 56, pagina, REGULAR, 8, SECUNDARIO);
                    String referencia = "N.° " + codigo;
                    texto(MARGEN + ANCHO_UTIL - ancho(referencia, REGULAR, 8), 45, referencia, REGULAR, 8, SECUNDARIO);
                }
            }
            contenido = null;
        }

        private void nuevaPagina() throws IOException {
            cerrar();
            PDPage pagina = new PDPage(PDRectangle.A4);
            pdf.addPage(pagina);
            contenido = new PDPageContentStream(pdf, pagina);
            encabezado();
            y = INICIO_CUERPO;
        }

        private void encabezado() throws IOException {
            float altoLogo = 44f;
            float anchoLogo = logo == null ? 0 : altoLogo * logo.getWidth() / logo.getHeight();
            if (logo != null) {
                contenido.drawImage(logo, MARGEN, ALTO - 40 - altoLogo, anchoLogo, altoLogo);
            }
            float derecha = MARGEN + ANCHO_UTIL;
            texto(derecha - ancho(ENTIDAD, NEGRITA, 10), ALTO - 52, ENTIDAD, NEGRITA, 10, AZUL);
            String areaLimpia = limpiar(area);
            texto(derecha - ancho(areaLimpia, REGULAR, 9), ALTO - 65, areaLimpia, REGULAR, 9, SECUNDARIO);
            String pais = "Lima - Perú";
            texto(derecha - ancho(pais, REGULAR, 8), ALTO - 77, pais, REGULAR, 8, SECUNDARIO);
            rectangulo(MARGEN, ALTO - 96, ANCHO_UTIL, 2.2f, AZUL);
            rectangulo(MARGEN, ALTO - 99, ANCHO_UTIL * 0.35f, 1.4f, CELESTE);
        }

        private void asegurar(float alto) throws IOException {
            if (y - alto < FIN_CUERPO) {
                nuevaPagina();
            }
        }

        private void centrado(String valor, PDFont fuente, float tamano, Color color) throws IOException {
            String limpio = limpiar(valor);
            texto(MARGEN + (ANCHO_UTIL - ancho(limpio, fuente, tamano)) / 2, y, limpio, fuente, tamano, color);
        }

        private void justificada(String linea, PDFont fuente, float tamano, Color color) throws IOException {
            String[] palabras = linea.split(" ");
            if (palabras.length < 2) {
                texto(MARGEN, y, linea, fuente, tamano, color);
                return;
            }
            float ocupado = 0;
            for (String palabra : palabras) {
                ocupado += ancho(palabra, fuente, tamano);
            }
            float hueco = (ANCHO_UTIL - ocupado) / (palabras.length - 1);
            if (hueco > tamano * 1.2f) {
                texto(MARGEN, y, linea, fuente, tamano, color);
                return;
            }
            float x = MARGEN;
            for (String palabra : palabras) {
                texto(x, y, palabra, fuente, tamano, color);
                x += ancho(palabra, fuente, tamano) + hueco;
            }
        }

        private void texto(float x, float linea, String valor, PDFont fuente, float tamano, Color color)
                throws IOException {
            contenido.beginText();
            contenido.setNonStrokingColor(color);
            contenido.setFont(fuente, tamano);
            contenido.newLineAtOffset(x, linea);
            contenido.showText(limpiar(valor));
            contenido.endText();
        }

        private void linea(float x1, float altura, float x2, Color color, float grosor) throws IOException {
            contenido.setStrokingColor(color);
            contenido.setLineWidth(grosor);
            contenido.moveTo(x1, altura);
            contenido.lineTo(x2, altura);
            contenido.stroke();
        }

        private void rectangulo(float x, float altura, float ancho, float alto, Color color) throws IOException {
            contenido.setNonStrokingColor(color);
            contenido.addRect(x, altura, ancho, alto);
            contenido.fill();
        }
    }

    private static PDImageXObject cargarLogo(PDDocument pdf) {
        try (InputStream entrada = new ClassPathResource("documentos/logo-san-miguel.png").getInputStream()) {
            return PDImageXObject.createFromByteArray(pdf, entrada.readAllBytes(), "logo-san-miguel.png");
        } catch (IOException ex) {
            return null;
        }
    }

    private static String recortar(String valor, PDFont fuente, float tamano, float maximo) throws IOException {
        String texto = limpiar(valor);
        if (ancho(texto, fuente, tamano) <= maximo) {
            return texto;
        }
        while (texto.length() > 1 && ancho(texto + "...", fuente, tamano) > maximo) {
            texto = texto.substring(0, texto.length() - 1);
        }
        return texto + "...";
    }

    static List<String> lineas(String valor, PDFont fuente, float tamano, float maximo) throws IOException {
        List<String> resultado = new ArrayList<>();
        String actual = "";
        for (String palabra : limpiar(valor).replace('\n', ' ').split(" ")) {
            if (palabra.isEmpty()) {
                continue;
            }
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (ancho(candidato, fuente, tamano) <= maximo) {
                actual = candidato;
                continue;
            }
            if (!actual.isEmpty()) {
                resultado.add(actual);
            }
            String resto = palabra;
            while (ancho(resto, fuente, tamano) > maximo && resto.length() > 1) {
                int corte = resto.length() - 1;
                while (corte > 1 && ancho(resto.substring(0, corte), fuente, tamano) > maximo) {
                    corte--;
                }
                resultado.add(resto.substring(0, corte));
                resto = resto.substring(corte);
            }
            actual = resto;
        }
        if (!actual.isEmpty() || resultado.isEmpty()) {
            resultado.add(actual);
        }
        return resultado;
    }

    private static float ancho(String valor, PDFont fuente, float tamano) throws IOException {
        return fuente.getStringWidth(limpiar(valor)) / 1000f * tamano;
    }

    /* La fuente estándar usa WinAnsi: se reemplaza lo que no puede representar. */
    static String limpiar(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder(valor.length());
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '\n' -> texto.append('\n');
                case '\t' -> texto.append(' ');
                case '–', '—' -> texto.append('-');
                case '‘', '’' -> texto.append('\'');
                case '“', '”' -> texto.append('"');
                case '…' -> texto.append("...");
                case '•' -> texto.append('-');
                case ' ', ' ' -> texto.append(' ');
                default -> texto.append(c >= 32 && c <= 255 && !(c >= 127 && c <= 159) ? c : ' ');
            }
        }
        return texto.toString();
    }
}
