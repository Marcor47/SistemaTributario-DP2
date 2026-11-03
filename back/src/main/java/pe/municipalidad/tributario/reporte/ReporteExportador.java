package pe.municipalidad.tributario.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaBuenPagador;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaMoroso;
import pe.municipalidad.tributario.reporte.ReporteRespuesta.FilaPrico;

/*
 * Generación de archivos exportables en Excel y PDF para los reportes del catálogo (HU007, RF-008).
 */
@Component
public class ReporteExportador {

    public static final String MEDIA_TYPE_EXCEL =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MEDIA_TYPE_PDF = "application/pdf";

    private static final Map<TipoReporte, Set<String>> COLUMNAS_VALIDAS = Map.of(
            TipoReporte.MOROSOS, Set.of("codigo", "nombre", "razonSocial", "regimen", "tipoDocumento", "numeroDocumento",
                    "condicion", "tipoPersona", "correo", "telefonos", "domicilioFiscal", "via", "numero", "departamento", "interior", "manzana", "lote", "distrito",
                    "zonas", "deudaVencida", "deudaSinBeneficio", "cuotasVencidas",
                    "vencimientoMasAntiguo", "diasVencido", "etapaCobranza", "nombreEtapa", "deudaPorTributo", "detalleDeuda"),
            TipoReporte.PRICOS, Set.of("codigo", "nombre", "tipoPersona", "tipoDocumento",
                    "numeroDocumento", "domicilioFiscal", "zonas", "emitidoAnio", "pagadoAnio",
                    "porcentajePagado", "deudaVencida"),
            TipoReporte.BUENOS_PAGADORES, Set.of("codigo", "nombre", "razonSocial", "regimen", "tipoDocumento",
                    "numeroDocumento", "domicilioFiscal", "zonas", "montoEmitidoPredial", "montoEmitidoArbitrios",
                    "montoPagadoPredial", "montoPagadoArbitrios", "totalEmitido", "totalPagado",
                    "cuotasPagadasATiempo", "pagadoAnio")
    );

    public static final Map<TipoReporte, List<String>> COLUMNAS_DEFECTO = Map.of(
            TipoReporte.MOROSOS, List.of("codigo", "razonSocial", "regimen", "tipoDocumento", "numeroDocumento", "condicion", "tipoPersona", "correo", "telefonos",
                    "zonas", "deudaVencida", "cuotasVencidas", "vencimientoMasAntiguo", "diasVencido", "nombreEtapa"),
            TipoReporte.PRICOS, List.of("codigo", "nombre", "tipoPersona", "tipoDocumento",
                    "numeroDocumento", "zonas", "emitidoAnio", "pagadoAnio", "porcentajePagado", "deudaVencida"),
            TipoReporte.BUENOS_PAGADORES, List.of("codigo", "razonSocial", "regimen", "tipoDocumento",
                    "numeroDocumento", "domicilioFiscal", "zonas", "montoEmitidoPredial", "montoEmitidoArbitrios",
                    "montoPagadoPredial", "montoPagadoArbitrios", "cuotasPagadasATiempo", "totalPagado")
    );

    public void validarColumnas(TipoReporte tipo, List<String> columnas) {
        if (columnas == null || columnas.isEmpty()) {
            return;
        }
        Set<String> permitidas = COLUMNAS_VALIDAS.getOrDefault(tipo, Set.of());
        for (String col : columnas) {
            if (!permitidas.contains(col)) {
                throw new DatoInvalidoException("COLUMNA_NO_VALIDA",
                        "La columna '" + col + "' no es válida para el " + tipo.getTitulo().toLowerCase() + ".");
            }
        }
    }

    public List<String> columnasEfectivas(TipoReporte tipo, List<String> columnasSolicitadas) {
        if (columnasSolicitadas != null && !columnasSolicitadas.isEmpty()) {
            validarColumnas(tipo, columnasSolicitadas);
            return columnasSolicitadas;
        }
        return COLUMNAS_DEFECTO.getOrDefault(tipo, List.of("codigo", "nombre"));
    }

    public ExportacionReporte exportar(ReporteRespuesta<?> reporte, FormatoExportacion formato,
            List<String> columnasSolicitadas, String emisor) {
        List<String> columnas = columnasEfectivas(reporte.tipoReporte(), columnasSolicitadas);
        String baseNombre = "reporte-" + reporte.tipoReporte().name().toLowerCase() + "-"
                + reporte.fechaCorte().format(DateTimeFormatter.BASIC_ISO_DATE);

        return switch (formato) {
            case EXCEL -> {
                byte[] bytes = generarExcel(reporte, columnas, emisor);
                yield new ExportacionReporte(bytes, baseNombre + ".xlsx", MEDIA_TYPE_EXCEL);
            }
            case PDF -> {
                byte[] bytes = generarPdf(reporte, columnas, emisor);
                yield new ExportacionReporte(bytes, baseNombre + ".pdf", MEDIA_TYPE_PDF);
            }
            default -> throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        };
    }

    private byte[] generarExcel(ReporteRespuesta<?> reporte, List<String> columnas, String emisor) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(reporte.tipoReporte().name());

            // Estilos
            Font fontTitulo = workbook.createFont();
            fontTitulo.setBold(true);
            fontTitulo.setFontHeightInPoints((short) 13);
            fontTitulo.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle styleTitulo = workbook.createCellStyle();
            styleTitulo.setFont(fontTitulo);

            Font fontCabecera = workbook.createFont();
            fontCabecera.setBold(true);
            fontCabecera.setColor(IndexedColors.WHITE.getIndex());

            CellStyle styleCabecera = workbook.createCellStyle();
            styleCabecera.setFont(fontCabecera);
            styleCabecera.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            styleCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleCabecera.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(styleCabecera);

            DataFormat df = workbook.createDataFormat();

            CellStyle styleMoneda = workbook.createCellStyle();
            styleMoneda.setDataFormat(df.getFormat("\"S/ \"#,##0.00"));
            aplicarBordes(styleMoneda);

            CellStyle stylePorcentaje = workbook.createCellStyle();
            stylePorcentaje.setDataFormat(df.getFormat("0.00\"%\""));
            aplicarBordes(stylePorcentaje);

            CellStyle styleEntero = workbook.createCellStyle();
            styleEntero.setDataFormat(df.getFormat("#,##0"));
            aplicarBordes(styleEntero);

            CellStyle styleTexto = workbook.createCellStyle();
            aplicarBordes(styleTexto);

            // Bloque de cabecera institucional
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL");
            c0.setCellStyle(styleTitulo);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Gerencia de Rentas — " + reporte.titulo());

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Fecha de corte: " + reporte.fechaCorte()
                    + " | Cantidad: " + reporte.resumen().cantidad()
                    + " | " + reporte.resumen().conceptoMonto() + ": S/ " + reporte.resumen().montoTotal()
                    + (reporte.resumen().umbralPrico() != null
                            ? " | Umbral PRICO: S/ " + reporte.resumen().umbralPrico() : ""));

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Filtros aplicados: " + describirFiltros(reporte.filtrosAplicados())
                    + (emisor != null ? " | Solicitado por: " + emisor : ""));

            // Fila de encabezados de la tabla
            int numFila = 5;
            Row filaCabecera = sheet.createRow(numFila++);
            for (int col = 0; col < columnas.size(); col++) {
                Cell cell = filaCabecera.createCell(col);
                cell.setCellValue(etiquetaColumna(columnas.get(col)));
                cell.setCellStyle(styleCabecera);
            }

            // Datos
            for (Object fila : reporte.filas()) {
                Row row = sheet.createRow(numFila++);
                for (int col = 0; col < columnas.size(); col++) {
                    Cell cell = row.createCell(col);
                    String nombreCol = columnas.get(col);
                    Object valor = obtenerValor(fila, nombreCol);

                    if (valor instanceof BigDecimal bd) {
                        cell.setCellValue(bd.doubleValue());
                        if ("porcentajePagado".equals(nombreCol)) {
                            cell.setCellStyle(stylePorcentaje);
                        } else {
                            cell.setCellStyle(styleMoneda);
                        }
                    } else if (valor instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                        cell.setCellStyle(styleEntero);
                    } else {
                        cell.setCellValue(Objects.toString(valor, ""));
                        cell.setCellStyle(styleTexto);
                    }
                }
            }

            for (int col = 0; col < columnas.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error al generar el archivo Excel.", ex);
        }
    }

    private byte[] generarPdf(ReporteRespuesta<?> reporte, List<String> columnas, String emisor) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Hoja apaisada (A4 Landscape)
            PDRectangle formatoPagina = reporte.tipoReporte() == TipoReporte.BUENOS_PAGADORES
                    ? PDRectangle.A3 : PDRectangle.A4;
            PDRectangle pageSize = new PDRectangle(formatoPagina.getHeight(), formatoPagina.getWidth());
            float anchoPagina = pageSize.getWidth();
            float altoPagina = pageSize.getHeight();
            float margen = 36f;
            float anchoTabla = anchoPagina - (2 * margen);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float[] anchos = calcularAnchosColumnas(columnas, anchoTabla);

            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float y = altoPagina - margen;

            // Encabezado institucional
            cs.beginText();
            cs.setFont(fontBold, 13);
            cs.newLineAtOffset(margen, y);
            cs.showText(sanitizar("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL"));
            cs.endText();
            y -= 16;

            cs.beginText();
            cs.setFont(fontBold, 10);
            cs.newLineAtOffset(margen, y);
            cs.showText(sanitizar("Gerencia de Rentas - " + reporte.titulo()));
            cs.endText();
            y -= 14;

            cs.beginText();
            cs.setFont(fontRegular, 8);
            cs.newLineAtOffset(margen, y);
            String info = "Fecha de corte: " + reporte.fechaCorte()
                    + " | Contribuyentes: " + reporte.resumen().cantidad()
                    + " | " + reporte.resumen().conceptoMonto() + ": S/ " + reporte.resumen().montoTotal()
                    + " | Filtros: " + describirFiltros(reporte.filtrosAplicados());
            cs.showText(sanitizar(info));
            cs.endText();
            y -= 12;

            // Línea divisoria
            cs.setStrokingColor(0.7f, 0.7f, 0.7f);
            cs.setLineWidth(0.5f);
            cs.moveTo(margen, y);
            cs.lineTo(anchoPagina - margen, y);
            cs.stroke();
            y -= 10;

            dibujarCabeceraPdf(cs, margen, y, columnas, anchos, fontBold);
            y -= 16;

            float altoFila = 15f;
            int paginaActual = 1;

            for (Object fila : reporte.filas()) {
                if (y < margen + 20) {
                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(anchoPagina / 2 - 20, margen - 10);
                    cs.showText("Pagina " + paginaActual);
                    cs.endText();
                    cs.close();

                    paginaActual++;
                    page = new PDPage(pageSize);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = altoPagina - margen;

                    dibujarCabeceraPdf(cs, margen, y, columnas, anchos, fontBold);
                    y -= 16;
                }

                dibujarFilaPdf(cs, margen, y, fila, columnas, anchos, fontRegular);
                y -= altoFila;
            }

            cs.beginText();
            cs.setFont(fontRegular, 8);
            cs.newLineAtOffset(anchoPagina / 2 - 20, margen - 10);
            cs.showText("Pagina " + paginaActual);
            cs.endText();
            cs.close();

            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error al generar el archivo PDF.", ex);
        }
    }

    private void dibujarCabeceraPdf(PDPageContentStream cs, float x0, float y, List<String> columnas,
            float[] anchos, PDType1Font fontBold) throws IOException {
        float x = x0;
        for (int i = 0; i < columnas.size(); i++) {
            cs.beginText();
            cs.setFont(fontBold, 8);
            cs.newLineAtOffset(x + 2, y + 2);
            String texto = sanitizar(etiquetaColumna(columnas.get(i)));
            cs.showText(truncar(texto, anchos[i] - 4, fontBold, 8));
            cs.endText();
            x += anchos[i];
        }

        // Línea debajo de encabezado
        float anchoTotal = 0;
        for (float a : anchos) anchoTotal += a;
        cs.setStrokingColor(0.2f, 0.2f, 0.2f);
        cs.setLineWidth(0.8f);
        cs.moveTo(x0, y - 2);
        cs.lineTo(x0 + anchoTotal, y - 2);
        cs.stroke();
    }

    private void dibujarFilaPdf(PDPageContentStream cs, float x0, float y, Object fila, List<String> columnas,
            float[] anchos, PDType1Font font) throws IOException {
        float x = x0;
        for (int i = 0; i < columnas.size(); i++) {
            String col = columnas.get(i);
            Object val = obtenerValor(fila, col);
            String texto = formatearTexto(val, col);

            cs.beginText();
            cs.setFont(font, 7.5f);
            cs.newLineAtOffset(x + 2, y + 2);
            cs.showText(truncar(sanitizar(texto), anchos[i] - 4, font, 7.5f));
            cs.endText();
            x += anchos[i];
        }

        // Línea de separación sutil
        float anchoTotal = 0;
        for (float a : anchos) anchoTotal += a;
        cs.setStrokingColor(0.88f, 0.88f, 0.88f);
        cs.setLineWidth(0.4f);
        cs.moveTo(x0, y - 1);
        cs.lineTo(x0 + anchoTotal, y - 1);
        cs.stroke();
    }

    private static String formatearTexto(Object val, String columna) {
        if (val == null) return "";
        if (val instanceof BigDecimal bd) {
            if ("porcentajePagado".equals(columna)) {
                return bd.toPlainString() + "%";
            }
            return "S/ " + bd.toPlainString();
        }
        return val.toString();
    }

    private static String truncar(String texto, float anchoMax, PDType1Font font, float fontSize) {
        try {
            float ancho = font.getStringWidth(texto) / 1000f * fontSize;
            if (ancho <= anchoMax) return texto;
            while (texto.length() > 3) {
                texto = texto.substring(0, texto.length() - 1);
                if (font.getStringWidth(texto + "...") / 1000f * fontSize <= anchoMax) {
                    return texto + "...";
                }
            }
            return texto;
        } catch (IOException e) {
            return texto;
        }
    }

    private static float[] calcularAnchosColumnas(List<String> columnas, float anchoTabla) {
        float[] anchos = new float[columnas.size()];
        float totalPeso = 0;
        for (String col : columnas) {
            totalPeso += pesoColumna(col);
        }
        for (int i = 0; i < columnas.size(); i++) {
            anchos[i] = (pesoColumna(columnas.get(i)) / totalPeso) * anchoTabla;
        }
        return anchos;
    }

    private static float pesoColumna(String columna) {
        return switch (columna) {
            case "nombre", "razonSocial" -> 2.5f;
            case "regimen", "distrito", "via" -> 1.4f;
            case "deudaSinBeneficio" -> 1.2f;
            case "numero", "interior", "manzana", "lote" -> 0.8f;
            case "domicilioFiscal" -> 2.2f;
            case "deudaPorTributo" -> 2.0f;
            case "codigo", "tipoDocumento", "zonas" -> 0.9f;
            case "numeroDocumento", "deudaVencida", "emitidoAnio", "pagadoAnio" -> 1.2f;
            case "montoEmitidoPredial", "montoEmitidoArbitrios", "montoPagadoPredial", "montoPagadoArbitrios", "totalEmitido", "totalPagado" -> 1.3f;
            case "cuotasVencidas", "diasVencido", "cuotasPagadasATiempo", "porcentajePagado" -> 1.0f;
            case "vencimientoMasAntiguo", "etapaCobranza", "nombreEtapa" -> 1.3f;
            default -> 1.0f;
        };
    }

    private static void aplicarBordes(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    public static String etiquetaColumna(String columna) {
        return switch (columna) {
            case "codigo" -> "Código";
            case "razonSocial" -> "Razón Social";
            case "regimen" -> "Régimen";
            case "deudaSinBeneficio" -> "Deuda Sin Beneficio (S/)";
            case "via" -> "Vía";
            case "numero" -> "Número";
            case "distrito" -> "Distrito";
            case "departamento" -> "Departamento";
            case "interior" -> "Interior";
            case "manzana" -> "Manzana";
            case "lote" -> "Lote";
            case "nombre" -> "Contribuyente";
            case "tipoPersona" -> "Tipo Persona";
            case "tipoDocumento" -> "Tipo Doc.";
            case "numeroDocumento" -> "N° Documento";
            case "condicion" -> "Condición";
            case "correo" -> "Correo electrónico";
            case "telefonos" -> "Teléfonos";
            case "domicilioFiscal" -> "Domicilio Fiscal";
            case "zonas" -> "Zonas";
            case "deudaVencida" -> "Deuda Vencida (S/)";
            case "cuotasVencidas" -> "Cuotas Vencidas";
            case "vencimientoMasAntiguo" -> "Vencimiento Antiguo";
            case "diasVencido" -> "Días Vencido";
            case "etapaCobranza" -> "Etapa Cobranza";
            case "nombreEtapa" -> "Etapa de Cobranza";
            case "deudaPorTributo" -> "Deuda por Tributo";
            case "emitidoAnio" -> "Emitido (S/)";
            case "pagadoAnio" -> "Pagado (S/)";
            case "montoEmitidoPredial" -> "Emitido Predial (S/)";
            case "montoEmitidoArbitrios" -> "Emitido Arbitrios (S/)";
            case "montoPagadoPredial" -> "Pagado Predial (S/)";
            case "montoPagadoArbitrios" -> "Pagado Arbitrios (S/)";
            case "totalEmitido" -> "Total Emitido (S/)";
            case "totalPagado" -> "Total Pagado (S/)";
            case "porcentajePagado" -> "% Pagado";
            case "cuotasPagadasATiempo" -> "Cuotas a Tiempo";
            default -> columna;
        };
    }

    public static Object obtenerValor(Object fila, String columna) {
        if (fila instanceof FilaMoroso m) {
            return switch (columna) {
                case "codigo" -> m.codigo();
                case "nombre" -> m.nombre();
                case "tipoDocumento" -> m.tipoDocumento() != null ? m.tipoDocumento().name() : "";
                case "numeroDocumento" -> m.numeroDocumento();
                case "condicion" -> m.condicion();
                case "tipoPersona" -> m.tipoPersona() != null ? m.tipoPersona().name() : "";
                case "correo" -> m.correo();
                case "telefonos" -> m.telefonos();
                case "domicilioFiscal" -> m.domicilioFiscal();
                case "razonSocial" -> m.razonSocial() != null ? m.razonSocial() : m.nombre();
                case "regimen" -> m.regimen() != null ? m.regimen() : "";
                case "deudaSinBeneficio" -> m.deudaSinBeneficio() != null ? m.deudaSinBeneficio() : m.deudaVencida();
                case "via" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().via() != null ? m.domicilioFiscalDesglosado().via() : "";
                case "numero" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().numero() != null ? m.domicilioFiscalDesglosado().numero() : "";
                case "distrito" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().distrito() != null ? m.domicilioFiscalDesglosado().distrito() : "";
                case "departamento" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().departamento() != null ? m.domicilioFiscalDesglosado().departamento() : "";
                case "interior" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().interior() != null ? m.domicilioFiscalDesglosado().interior() : "";
                case "manzana" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().manzana() != null ? m.domicilioFiscalDesglosado().manzana() : "";
                case "lote" -> m.domicilioFiscalDesglosado() != null && m.domicilioFiscalDesglosado().lote() != null ? m.domicilioFiscalDesglosado().lote() : "";
                case "zonas" -> m.zonas() != null ? String.join(", ", m.zonas()) : "";
                case "deudaVencida" -> m.deudaVencida();
                case "cuotasVencidas" -> m.cuotasVencidas();
                case "vencimientoMasAntiguo" -> m.vencimientoMasAntiguo() != null ? m.vencimientoMasAntiguo().toString() : "";
                case "diasVencido" -> m.diasVencido();
                case "etapaCobranza" -> m.etapaCobranza() != null ? m.etapaCobranza().name() : "";
                case "nombreEtapa" -> m.nombreEtapa();
                case "deudaPorTributo" -> m.deudaPorTributo() != null
                        ? m.deudaPorTributo().stream()
                                .map(t -> t.tributo() + ": S/ " + t.monto())
                                .collect(Collectors.joining("; "))
                        : "";
                case "detalleDeuda" -> m.detalleDeuda();
                default -> "";
            };
        } else if (fila instanceof FilaPrico p) {
            return switch (columna) {
                case "codigo" -> p.codigo();
                case "nombre" -> p.nombre();
                case "tipoPersona" -> p.tipoPersona() != null ? p.tipoPersona().name() : "";
                case "tipoDocumento" -> p.tipoDocumento() != null ? p.tipoDocumento().name() : "";
                case "numeroDocumento" -> p.numeroDocumento();
                case "domicilioFiscal" -> p.domicilioFiscal();
                case "zonas" -> p.zonas() != null ? String.join(", ", p.zonas()) : "";
                case "emitidoAnio" -> p.emitidoAnio();
                case "pagadoAnio" -> p.pagadoAnio();
                case "porcentajePagado" -> p.porcentajePagado();
                case "deudaVencida" -> p.deudaVencida();
                default -> "";
            };
        } else if (fila instanceof FilaBuenPagador b) {
            return switch (columna) {
                case "codigo" -> b.codigo();
                case "nombre" -> b.nombre();
                case "razonSocial" -> b.razonSocial();
                case "regimen" -> b.regimen();
                case "tipoDocumento" -> b.tipoDocumento() != null ? b.tipoDocumento().name() : "";
                case "numeroDocumento" -> b.numeroDocumento();
                case "domicilioFiscal" -> b.domicilioFiscal();
                case "zonas" -> b.zonas() != null ? String.join(", ", b.zonas()) : "";
                case "montoEmitidoPredial" -> b.montoEmitidoPredial();
                case "montoEmitidoArbitrios" -> b.montoEmitidoArbitrios();
                case "montoPagadoPredial" -> b.montoPagadoPredial();
                case "montoPagadoArbitrios" -> b.montoPagadoArbitrios();
                case "totalEmitido" -> b.totalEmitido();
                case "totalPagado" -> b.totalPagado();
                case "cuotasPagadasATiempo" -> b.cuotasPagadasATiempo();
                case "pagadoAnio" -> b.pagadoAnio();
                default -> "";
            };
        }
        return "";
    }

    private static String describirFiltros(FiltrosReporte filtros) {
        if (filtros == null) return "Ninguno";
        List<String> partes = new ArrayList<>();
        if (filtros.anio() != null) partes.add("Año: " + filtros.anio());
        if (filtros.zona() != null) partes.add("Zona: " + filtros.zona());
        if (filtros.tributo() != null) partes.add("Tributo: " + filtros.tributo());
        if (filtros.montoMinimo() != null) partes.add("Monto mínimo: S/ " + filtros.montoMinimo());
        return partes.isEmpty() ? "Ninguno" : String.join(", ", partes);
    }

    private static String sanitizar(String texto) {
        if (texto == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                switch (c) {
                    case 'á', 'Á' -> sb.append('a');
                    case 'é', 'É' -> sb.append('e');
                    case 'í', 'Í' -> sb.append('i');
                    case 'ó', 'Ó' -> sb.append('o');
                    case 'ú', 'Ú' -> sb.append('u');
                    case 'ñ' -> sb.append('n');
                    case 'Ñ' -> sb.append('N');
                    case '°' -> sb.append('.');
                    default -> sb.append(' ');
                }
            }
        }
        return sb.toString();
    }
}

