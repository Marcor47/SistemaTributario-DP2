package pe.municipalidad.tributario.documentos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.documentos.DocumentoInstitucionalPdf.Dato;

/*
 * Formularios y plantillas oficiales de la Gerencia de Rentas con el formato institucional único (5.2): los mismos
 * bloques (encabezado con logo, título, código, lugar y fecha, secciones con campos, firma y pie con dirección y
 * numeración) en PDF, con DocumentoInstitucionalPdf, y en Word, con el equivalente en Apache POI.
 */
@Component
public class PlantillasInstitucionales {

    private static final String LINEA = "________________________________________________";
    private static final String AZUL = "17406B";
    private static final String GRIS = "526477";

    /* Un bloque del formulario: una sección con campos para completar o una tabla con filas en blanco. */
    sealed interface Bloque permits Campos, TablaVacia, Texto {
    }

    record Campos(String titulo, List<String> etiquetas) implements Bloque {
    }

    record TablaVacia(String titulo, List<String> columnas, int filas) implements Bloque {
    }

    record Texto(String titulo, String contenido) implements Bloque {
    }

    public record Formato(String codigo, String nombre, String area, boolean pdf, List<Bloque> bloques,
            String firmante) {

        public String archivo(int version) {
            return codigo + "-v" + version + (pdf ? ".pdf" : ".docx");
        }
    }

    private static final Map<String, Formato> FORMATOS = Map.of(
            "PL-001", new Formato("PL-001", "Constancia de No Adeudo", "Gerencia de Rentas", true, List.of(
                    new Campos("Datos del contribuyente", List.of("Nombre o razón social:", "DNI / RUC:",
                            "Código de contribuyente:", "Domicilio fiscal:")),
                    new Texto("Constancia", "La Gerencia de Rentas de la Municipalidad Distrital de San Miguel HACE "
                            + "CONSTAR que el contribuyente identificado en el presente documento no registra deuda "
                            + "tributaria pendiente por Impuesto Predial ni por Arbitrios Municipales, incluido el "
                            + "interés moratorio, a la fecha de emisión."),
                    new Campos("Datos de la emisión", List.of("Código de la constancia:", "Fecha de emisión:",
                            "Vigencia hasta:")),
                    new Texto("Base legal", "Texto Único Ordenado de la Ley de Tributación Municipal (D. S. N.° "
                            + "156-2004-EF) y Texto Único Ordenado del Código Tributario (D. S. N.° 133-2013-EF).")),
                    "Gerencia de Rentas"),
            "FO-001", new Formato("FO-001", "Hoja de Resumen (HR)", "Gerencia de Rentas", false, List.of(
                    new Campos("Datos del contribuyente", List.of("Apellidos y nombres o razón social:",
                            "Tipo y número de documento:", "Código de contribuyente:", "Domicilio fiscal:",
                            "Teléfono y correo electrónico:")),
                    new TablaVacia("Relación de predios", List.of("Código del predio", "Ubicación", "Uso",
                            "Área (m²)", "Autoavalúo (S/)"), 6),
                    new Texto("Declaración", "Declaro bajo juramento que la información consignada es verdadera y "
                            + "corresponde a los predios de mi propiedad ubicados en el distrito de San Miguel.")),
                    "Firma del contribuyente o representante legal"),
            "FO-002", new Formato("FO-002", "Declaración Jurada de Predio", "Gerencia de Rentas", false, List.of(
                    new Campos("Datos del declarante", List.of("Apellidos y nombres o razón social:",
                            "Tipo y número de documento:", "Condición (propietario, copropietario, poseedor):")),
                    new Campos("Ubicación del predio", List.of("Vía y número:", "Urbanización:", "Interior, manzana y lote:",
                            "Código catastral:")),
                    new Campos("Características del predio", List.of("Uso:", "Área del terreno (m²):",
                            "Área construida (m²):", "Material predominante:", "Año de construcción:",
                            "Fecha de adquisición:")),
                    new Texto("Declaración", "Declaro bajo juramento que los datos consignados son verdaderos, conforme "
                            + "al artículo 14 del TUO de la Ley de Tributación Municipal.")),
                    "Firma del declarante"),
            "FO-003", new Formato("FO-003", "Solicitud de Registro o Traspaso de Predio", "Gerencia de Rentas", true,
                    List.of(
                    new Texto("Tipo de trámite", "Marque con una X:   ( ) Registro de predio      ( ) Traspaso de predio"),
                    new Campos("Datos del solicitante", List.of("Apellidos y nombres o razón social:",
                            "Tipo y número de documento:", "Domicilio fiscal:", "Teléfono y correo electrónico:")),
                    new Campos("Datos del predio", List.of("Código del predio (traspaso):", "Ubicación:", "Uso:",
                            "Área del terreno (m²):")),
                    new Texto("Documentos que se adjuntan", "1. Documento que acredita la propiedad o la transferencia "
                            + "(minuta, escritura o título). 2. Copia del documento de identidad del adquirente. "
                            + "3. Declaración jurada de autoavalúo firmada.")),
                    "Firma del solicitante"));

    public Optional<Formato> formato(String codigo) {
        return Optional.ofNullable(FORMATOS.get(codigo));
    }

    public byte[] generar(Formato formato) {
        return formato.pdf() ? pdf(formato) : docx(formato);
    }

    private static byte[] pdf(Formato f) {
        DocumentoInstitucionalPdf documento = new DocumentoInstitucionalPdf(f.area(), f.nombre(), f.codigo(), null);
        for (Bloque bloque : f.bloques()) {
            switch (bloque) {
                case Campos c -> documento.seccion(c.titulo())
                        .datos(c.etiquetas().stream().map(e -> new Dato(e, LINEA)).toList());
                case TablaVacia t -> documento.seccion(t.titulo()).tabla(t.columnas(),
                        java.util.Collections.nCopies(t.filas(), t.columnas().stream().map(c -> " ").toList()),
                        proporciones(t.columnas().size()), new boolean[t.columnas().size()]);
                case Texto t -> documento.seccion(t.titulo()).parrafo(t.contenido());
            }
        }
        return documento.firma(f.firmante(), "Nombre, documento y fecha", null).generar();
    }

    private static float[] proporciones(int columnas) {
        float[] p = new float[columnas];
        java.util.Arrays.fill(p, 1f);
        p[Math.min(1, columnas - 1)] = 2.2f;
        return p;
    }

    private static byte[] docx(Formato f) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            margenes(doc);
            encabezado(doc, f.area());
            pie(doc, f.codigo());
            XWPFParagraph titulo = doc.createParagraph();
            titulo.setAlignment(ParagraphAlignment.CENTER);
            texto(titulo, f.nombre().toUpperCase(), true, 15, AZUL);
            XWPFParagraph codigo = doc.createParagraph();
            codigo.setAlignment(ParagraphAlignment.CENTER);
            texto(codigo, "N.° " + f.codigo(), true, 10, "1B9BC4");
            XWPFParagraph lugar = doc.createParagraph();
            lugar.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun l = lugar.createRun();
            l.setItalic(true);
            l.setFontSize(9);
            l.setColor(GRIS);
            l.setFontFamily("Arial");
            l.setText("San Miguel, ____ de ________________ de 20____");
            for (Bloque bloque : f.bloques()) {
                seccion(doc, switch (bloque) {
                    case Campos c -> c.titulo();
                    case TablaVacia t -> t.titulo();
                    case Texto t -> t.titulo();
                });
                switch (bloque) {
                    case Campos c -> c.etiquetas().forEach(e -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setSpacingAfter(120);
                        texto(p, e + " ", true, 10, GRIS);
                        texto(p, LINEA, false, 10, "202934");
                    });
                    case TablaVacia t -> tabla(doc, t);
                    case Texto t -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setAlignment(ParagraphAlignment.BOTH);
                        texto(p, t.contenido(), false, 10, "202934");
                    }
                }
            }
            firma(doc, f.firmante());
            doc.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar la plantilla " + f.codigo(), ex);
        }
    }

    private static void margenes(XWPFDocument doc) {
        CTSectPr seccion = doc.getDocument().getBody().addNewSectPr();
        CTPageMar margen = seccion.addNewPgMar();
        BigInteger normal = BigInteger.valueOf(1134);
        margen.setLeft(normal);
        margen.setRight(normal);
        margen.setTop(BigInteger.valueOf(1700));
        margen.setBottom(BigInteger.valueOf(1300));
        margen.setHeader(BigInteger.valueOf(500));
        margen.setFooter(BigInteger.valueOf(500));
    }

    private static void encabezado(XWPFDocument doc, String area) throws IOException {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFTable tabla = header.createTable(1, 2);
        tabla.removeBorders();
        tabla.setWidth("100%");
        XWPFParagraph logo = tabla.getRow(0).getCell(0).getParagraphs().get(0);
        try (InputStream imagen = new ClassPathResource("documentos/logo-san-miguel.png").getInputStream()) {
            logo.createRun().addPicture(imagen, Document.PICTURE_TYPE_PNG, "logo-san-miguel.png",
                    Units.toEMU(106), Units.toEMU(44));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
            throw new IOException(ex);
        }
        XWPFParagraph entidad = tabla.getRow(0).getCell(1).getParagraphs().get(0);
        entidad.setAlignment(ParagraphAlignment.RIGHT);
        texto(entidad, DocumentoInstitucionalPdf.ENTIDAD, true, 10, AZUL);
        XWPFParagraph areaParrafo = tabla.getRow(0).getCell(1).addParagraph();
        areaParrafo.setAlignment(ParagraphAlignment.RIGHT);
        texto(areaParrafo, area + " · Lima - Perú", false, 9, GRIS);
    }

    private static void pie(XWPFDocument doc, String codigo) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph linea = footer.createParagraph();
        texto(linea, DocumentoInstitucionalPdf.ENTIDAD + " - Gerencia de Rentas", true, 8, AZUL);
        XWPFParagraph datos = footer.createParagraph();
        texto(datos, DocumentoInstitucionalPdf.DIRECCION + "  |  " + DocumentoInstitucionalPdf.PORTAL
                + "  |  N.° " + codigo + "  |  Página ", false, 8, GRIS);
        datos.getCTP().addNewFldSimple().setInstr("PAGE");
    }

    private static void seccion(XWPFDocument doc, String titulo) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        texto(p, titulo.toUpperCase(), true, 10, AZUL);
    }

    private static void tabla(XWPFDocument doc, TablaVacia t) {
        XWPFTable tabla = doc.createTable(t.filas() + 1, t.columnas().size());
        tabla.setWidth("100%");
        XWPFTableRow cabecera = tabla.getRow(0);
        for (int c = 0; c < t.columnas().size(); c++) {
            cabecera.getCell(c).setColor(AZUL);
            texto(cabecera.getCell(c).getParagraphs().get(0), t.columnas().get(c), true, 9, "FFFFFF");
        }
        for (int r = 1; r <= t.filas(); r++) {
            tabla.getRow(r).setHeight(380);
        }
    }

    private static void firma(XWPFDocument doc, String firmante) {
        for (int i = 0; i < 3; i++) {
            doc.createParagraph();
        }
        XWPFParagraph linea = doc.createParagraph();
        linea.setAlignment(ParagraphAlignment.CENTER);
        texto(linea, "______________________________________", false, 10, GRIS);
        XWPFParagraph nombre = doc.createParagraph();
        nombre.setAlignment(ParagraphAlignment.CENTER);
        texto(nombre, firmante, true, 10, "202934");
        XWPFParagraph detalle = doc.createParagraph();
        detalle.setAlignment(ParagraphAlignment.CENTER);
        texto(detalle, "Nombre, documento y fecha", false, 9, GRIS);
    }

    private static void texto(XWPFParagraph p, String valor, boolean negrita, int tamano, String color) {
        XWPFRun run = p.createRun();
        run.setFontFamily("Arial");
        run.setBold(negrita);
        run.setFontSize(tamano);
        run.setColor(color);
        run.setText(valor);
    }
}
