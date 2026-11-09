package pe.municipalidad.tributario.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.gestiontributaria.DetalleDeuda;
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

    private final Clock reloj;

    public ReporteExportador(Clock reloj) {
        this.reloj = reloj;
    }

    private static final Map<TipoReporte, Set<String>> COLUMNAS_VALIDAS = Map.of(
            TipoReporte.MOROSOS, Set.of("codigo", "nombre", "razonSocial", "regimen", "tipoDocumento", "numeroDocumento",
                    "condicion", "tipoPersona", "correo", "telefonos", "domicilioFiscal", "via", "numero", "departamento", "interior", "manzana", "lote", "distrito",
                    "zonas", "deudaVencida", "deudaSinBeneficio", "cuotasVencidas",
                    "vencimientoMasAntiguo", "diasVencido", "etapaCobranza", "nombreEtapa", "deudaPorTributo", "detalleDeuda"),
            TipoReporte.PRICOS, Set.of("codigo", "nombre", "tipoPersona", "tipoDocumento",
                    "numeroDocumento", "domicilioFiscal", "zonas", "emitidoAnio", "pagadoAnio",
                    "porcentajePagado", "deudaVencida", "razonSocial", "regimen", "via", "numero",
                    "departamento", "interior", "manzana", "lote", "distrito", "baseImponible",
                    "emisionPredial", "emisionArbitrios", "deudaAnteriorPredial", "deudaAnteriorArbitrios",
                    "deudaActualPredial", "deudaActualArbitrios", "porcentajeDescuentoArbitrios",
                    "condicion", "correo", "telefonos"),
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

    public static final Set<String> COLUMNAS_DETALLE_MOROSOS = Set.of(
            "anio", "codigoPredio", "zona", "tributo", "periodo", "fechaVencimiento", "diasVencido",
            "etapaCobranza", "montoSinBeneficio", "montoConBeneficio");

    private record GrupoColumnas(String etiqueta, List<String> columnas) {
    }

    private record RangoGrupo(String etiqueta, int desde, int hasta) {
    }

    private static final List<GrupoColumnas> GRUPOS_MOROSOS = List.of(
            new GrupoColumnas("Contribuyente", List.of("codigo", "razonSocial", "regimen", "tipoDocumento", "numeroDocumento")),
            new GrupoColumnas("Domicilio fiscal", List.of("domicilioFiscal", "via", "numero", "departamento",
                    "interior", "manzana", "lote", "distrito", "zonas")),
            new GrupoColumnas("Deuda vencida", List.of("deudaVencida", "deudaSinBeneficio")),
            new GrupoColumnas("Datos del contribuyente", List.of("condicion", "tipoPersona", "correo", "telefonos"))
    );

    private static final List<GrupoColumnas> GRUPOS_DETALLE_MOROSOS = List.of(
            new GrupoColumnas("Contribuyente", List.of("codigo", "razonSocial")),
            new GrupoColumnas("Obligación", List.of("anio", "codigoPredio", "zona", "tributo", "periodo")),
            new GrupoColumnas("Vencimiento", List.of("fechaVencimiento", "diasVencido", "etapaCobranza")),
            new GrupoColumnas("Montos", List.of("montoSinBeneficio", "montoConBeneficio"))
    );

    /* Fila plana del detalle de obligaciones para las exportaciones de morosos. */
    private record FilaDetalle(String codigo, String razonSocial, DetalleDeuda detalle) {
    }

    public void validarColumnasDetalle(List<String> columnas) {
        if (columnas == null || columnas.isEmpty()) {
            return;
        }
        for (String col : columnas) {
            if (!COLUMNAS_DETALLE_MOROSOS.contains(col)) {
                throw new DatoInvalidoException("COLUMNA_NO_VALIDA",
                        "La columna de detalle '" + col + "' no es válida.");
            }
        }
    }

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

    public List<String> columnasDetalleEfectivas(List<String> columnasSolicitadas) {
        if (columnasSolicitadas == null || columnasSolicitadas.isEmpty()) {
            return List.of();
        }
        validarColumnasDetalle(columnasSolicitadas);
        return columnasSolicitadas;
    }

    public ExportacionReporte exportar(ReporteRespuesta<?> reporte, FormatoExportacion formato,
            List<String> columnasSolicitadas, String emisor) {
        return exportar(reporte, formato, columnasSolicitadas, List.of(), null, emisor, null);
    }

    public ExportacionReporte exportar(ReporteRespuesta<?> reporte, FormatoExportacion formato,
            List<String> columnasSolicitadas, List<String> columnasDetalleSolicitadas,
            String orden, String emisor) {
        return exportar(reporte, formato, columnasSolicitadas, columnasDetalleSolicitadas, orden, emisor, null);
    }

    public ExportacionReporte exportar(ReporteRespuesta<?> reporte, FormatoExportacion formato,
            List<String> columnasSolicitadas, List<String> columnasDetalleSolicitadas,
            String orden, String emisor, String nombreVista) {
        List<String> columnas = columnasEfectivas(reporte.tipoReporte(), columnasSolicitadas);
        List<String> columnasDetalle = reporte.tipoReporte() == TipoReporte.MOROSOS
                ? columnasDetalleEfectivas(columnasDetalleSolicitadas) : List.of();
        List<?> filas = ordenarFilas(reporte.filas(), orden);
        String nombreArchivo = construirNombreArchivo(reporte.tipoReporte(), formato, nombreVista);

        return switch (formato) {
            case EXCEL -> {
                byte[] bytes = generarExcel(reporte, filas, columnas, columnasDetalle, emisor);
                yield new ExportacionReporte(bytes, nombreArchivo, MEDIA_TYPE_EXCEL);
            }
            case PDF -> {
                byte[] bytes = generarPdf(reporte, filas, columnas, columnasDetalle, emisor);
                yield new ExportacionReporte(bytes, nombreArchivo, MEDIA_TYPE_PDF);
            }
            default -> throw new DatoInvalidoException("FORMATO_NO_SOPORTADO",
                    "El formato de exportación debe ser EXCEL o PDF.");
        };
    }

    private String construirNombreArchivo(TipoReporte tipo, FormatoExportacion formato, String nombreVista) {
        StringBuilder nombre = new StringBuilder("reporte_")
                .append(nombreTipo(tipo))
                .append('_')
                .append(LocalDate.now(reloj).format(DateTimeFormatter.BASIC_ISO_DATE));
        String vista = limpiarNombreVista(nombreVista);
        if (!vista.isEmpty()) {
            nombre.append('_').append(vista);
        }
        String extension = formato == FormatoExportacion.EXCEL ? ".xlsx"
                : formato == FormatoExportacion.PDF ? ".pdf" : "";
        return nombre.append(extension).toString();
    }

    private static String nombreTipo(TipoReporte tipo) {
        return switch (tipo) {
            case MOROSOS -> "morosos";
            case PRICOS -> "pricos";
            case BUENOS_PAGADORES -> "buenos_pagadores";
        };
    }

    private static String limpiarNombreVista(String nombre) {
        if (nombre == null) {
            return "";
        }
        String limpio = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_.-]+|[_.-]+$", "");
        if (limpio.length() > 80) {
            limpio = limpio.substring(0, 80).replaceAll("[_.-]+$", "");
        }
        return limpio;
    }

    private static List<?> ordenarFilas(List<?> filas, String orden) {
        if (filas == null || filas.size() < 2 || orden == null || orden.isBlank()) {
            return filas;
        }
        String[] partes = orden.split(":");
        if (partes.length != 2) {
            return filas;
        }
        String campo = partes[0].trim();
        boolean descendente = "desc".equalsIgnoreCase(partes[1].trim());
        List<Object> copia = new ArrayList<>(filas);
        copia.sort((a, b) -> comparar(obtenerValor(a, campo), obtenerValor(b, campo), descendente));
        return copia;
    }

    private static int comparar(Object a, Object b, boolean descendente) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        int resultado;
        if (a instanceof Number na && b instanceof Number nb) {
            resultado = new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString()));
        } else {
            resultado = a.toString().compareToIgnoreCase(b.toString());
        }
        return descendente ? -resultado : resultado;
    }

    private static List<Object> filasDetalle(List<?> filas, List<String> columnasDetalle) {
        if (columnasDetalle == null || columnasDetalle.isEmpty()) {
            return List.of();
        }
        List<Object> detalle = new ArrayList<>();
        for (Object fila : filas) {
            if (fila instanceof FilaMoroso m && m.detalleDeuda() != null) {
                String nombre = m.razonSocial() != null ? m.razonSocial() : m.nombre();
                for (DetalleDeuda obligacion : m.detalleDeuda()) {
                    detalle.add(new FilaDetalle(m.codigo(), nombre, obligacion));
                }
            }
        }
        return detalle;
    }

    private static List<String> columnasDetalleTabla(List<String> columnasDetalle) {
        List<String> columnas = new ArrayList<>();
        columnas.add("codigo");
        columnas.add("razonSocial");
        columnas.addAll(columnasDetalle);
        return columnas;
    }

    private static List<GrupoColumnas> gruposPricos(int anioCorte) {
        return List.of(
                new GrupoColumnas("Contribuyente", List.of("codigo", "razonSocial", "regimen", "tipoDocumento", "numeroDocumento")),
                new GrupoColumnas("Domicilio fiscal", List.of("domicilioFiscal", "via", "numero", "departamento",
                        "interior", "manzana", "lote", "distrito")),
                new GrupoColumnas("Base imponible", List.of("baseImponible")),
                new GrupoColumnas("Emisión", List.of("emisionPredial", "emisionArbitrios")),
                new GrupoColumnas("Deuda años anteriores", List.of("deudaAnteriorPredial", "deudaAnteriorArbitrios")),
                new GrupoColumnas("Deuda con " + anioCorte, List.of("deudaActualPredial", "deudaActualArbitrios",
                        "porcentajeDescuentoArbitrios")),
                new GrupoColumnas("Datos del contribuyente", List.of("condicion", "tipoPersona", "correo", "telefonos"))
        );
    }

    private static List<GrupoColumnas> gruposExportacion(TipoReporte tipo, int anioCorte) {
        return switch (tipo) {
            case PRICOS -> gruposPricos(anioCorte);
            case MOROSOS -> GRUPOS_MOROSOS;
            default -> List.of();
        };
    }

    private static List<RangoGrupo> rangosGrupos(List<String> columnas, List<GrupoColumnas> grupos) {
        if (columnas == null || columnas.isEmpty() || grupos == null || grupos.isEmpty()) {
            return List.of();
        }
        Map<String, GrupoColumnas> porColumna = new HashMap<>();
        for (GrupoColumnas grupo : grupos) {
            for (String columna : grupo.columnas()) {
                porColumna.put(columna, grupo);
            }
        }
        List<RangoGrupo> rangos = new ArrayList<>();
        int i = 0;
        while (i < columnas.size()) {
            GrupoColumnas grupo = porColumna.get(columnas.get(i));
            if (grupo == null) {
                rangos.add(new RangoGrupo(etiquetaColumna(columnas.get(i)), i, i));
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < columnas.size() && grupo.columnas().contains(columnas.get(j + 1))) {
                j++;
            }
            rangos.add(new RangoGrupo(grupo.etiqueta(), i, j));
            i = j + 1;
        }
        return rangos;
    }

    private static void escribirCabeceraGruposExcel(Sheet sheet, int numFila, List<String> columnas,
            List<GrupoColumnas> grupos, CellStyle estilo) {
        List<RangoGrupo> rangos = rangosGrupos(columnas, grupos);
        if (rangos.isEmpty()) {
            return;
        }
        Row fila = sheet.createRow(numFila);
        fila.setHeightInPoints(26);
        for (RangoGrupo rango : rangos) {
            for (int col = rango.desde(); col <= rango.hasta(); col++) {
                fila.createCell(col).setCellStyle(estilo);
            }
            fila.getCell(rango.desde()).setCellValue(rango.etiqueta());
            if (rango.hasta() > rango.desde()) {
                sheet.addMergedRegion(new CellRangeAddress(numFila, numFila, rango.desde(), rango.hasta()));
            }
        }
    }

    private byte[] generarExcel(ReporteRespuesta<?> reporte, List<?> filas, List<String> columnas,
            List<String> columnasDetalle, String emisor) {
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
            fontCabecera.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle styleCabecera = workbook.createCellStyle();
            styleCabecera.setFont(fontCabecera);
            styleCabecera.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            styleCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleCabecera.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(styleCabecera);

            CellStyle styleGrupoCabecera = workbook.createCellStyle();
            styleGrupoCabecera.setFont(fontCabecera);
            styleGrupoCabecera.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            styleGrupoCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleGrupoCabecera.setAlignment(HorizontalAlignment.CENTER);
            styleGrupoCabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            styleGrupoCabecera.setWrapText(true);
            aplicarBordes(styleGrupoCabecera);

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

            for (int fila = 0; fila <= 3; fila++) {
                sheet.addMergedRegion(new CellRangeAddress(fila, fila, 0, 3));
            }

            // Fila de encabezados de la tabla
            List<GrupoColumnas> grupos = gruposExportacion(reporte.tipoReporte(), reporte.fechaCorte().getYear());
            escribirCabeceraGruposExcel(sheet, 4, columnas, grupos, styleGrupoCabecera);
            int numFila = 5;
            Row filaCabecera = sheet.createRow(numFila++);
            for (int col = 0; col < columnas.size(); col++) {
                Cell cell = filaCabecera.createCell(col);
                cell.setCellValue(etiquetaColumna(columnas.get(col)));
                cell.setCellStyle(styleCabecera);
            }

            // Datos
            for (Object fila : filas) {
                Row row = sheet.createRow(numFila++);
                for (int col = 0; col < columnas.size(); col++) {
                    Cell cell = row.createCell(col);
                    escribirCelda(cell, fila, columnas.get(col), styleMoneda, stylePorcentaje, styleEntero, styleTexto);
                }
            }

            // Detalle de obligaciones en una hoja independiente
            List<Object> detalle = filasDetalle(filas, columnasDetalle);
            List<String> columnasDetalleTabla = columnasDetalleTabla(columnasDetalle);
            if (!detalle.isEmpty()) {
                Sheet hojaDetalle = workbook.createSheet("Detalle");
                int filaDetalle = 0;

                Row tituloDetalle = hojaDetalle.createRow(filaDetalle++);
                Cell celdaTitulo = tituloDetalle.createCell(0);
                celdaTitulo.setCellValue("Detalle de obligaciones");
                celdaTitulo.setCellStyle(styleTitulo);

                escribirCabeceraGruposExcel(hojaDetalle, filaDetalle++, columnasDetalleTabla, GRUPOS_DETALLE_MOROSOS, styleGrupoCabecera);
                Row cabeceraDetalle = hojaDetalle.createRow(filaDetalle++);
                for (int col = 0; col < columnasDetalleTabla.size(); col++) {
                    Cell cell = cabeceraDetalle.createCell(col);
                    cell.setCellValue(etiquetaColumna(columnasDetalleTabla.get(col)));
                    cell.setCellStyle(styleCabecera);
                }

                for (Object fila : detalle) {
                    Row row = hojaDetalle.createRow(filaDetalle++);
                    for (int col = 0; col < columnasDetalleTabla.size(); col++) {
                        Cell cell = row.createCell(col);
                        escribirCelda(cell, fila, columnasDetalleTabla.get(col),
                                styleMoneda, stylePorcentaje, styleEntero, styleTexto);
                    }
                }

                for (int col = 0; col < columnasDetalleTabla.size(); col++) {
                    hojaDetalle.autoSizeColumn(col);
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

    private static void escribirCelda(Cell cell, Object fila, String columna,
            CellStyle styleMoneda, CellStyle stylePorcentaje, CellStyle styleEntero, CellStyle styleTexto) {
        Object valor = obtenerValor(fila, columna);
        if (valor instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
            cell.setCellStyle("porcentajePagado".equals(columna) ? stylePorcentaje : styleMoneda);
        } else if (valor instanceof Number num) {
            cell.setCellValue(num.doubleValue());
            cell.setCellStyle(styleEntero);
        } else {
            cell.setCellValue(Objects.toString(valor, ""));
            cell.setCellStyle(styleTexto);
        }
    }

    private byte[] generarPdf(ReporteRespuesta<?> reporte, List<?> filas, List<String> columnas,
            List<String> columnasDetalle, String emisor) {
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

            for (Object fila : filas) {
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

            // Detalle de obligaciones (solo morosos con columnas de detalle seleccionadas)
            List<Object> detalle = filasDetalle(filas, columnasDetalle);
            List<String> columnasDetalleTabla = columnasDetalleTabla(columnasDetalle);
            if (!detalle.isEmpty()) {
                float[] anchosDetalle = calcularAnchosColumnas(columnasDetalleTabla, anchoTabla);
                y -= 18;
                if (y < margen + 60) {
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
                }

                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.newLineAtOffset(margen, y);
                cs.showText(sanitizar("Detalle de obligaciones"));
                cs.endText();
                y -= 14;

                dibujarCabeceraPdf(cs, margen, y, columnasDetalleTabla, anchosDetalle, fontBold);
                y -= 16;

                for (Object fila : detalle) {
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

                        dibujarCabeceraPdf(cs, margen, y, columnasDetalleTabla, anchosDetalle, fontBold);
                        y -= 16;
                    }

                    dibujarFilaPdf(cs, margen, y, fila, columnasDetalleTabla, anchosDetalle, fontRegular);
                    y -= altoFila;
                }
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
            case "codigoPredio", "zona", "fechaVencimiento", "montoSinBeneficio", "montoConBeneficio" -> 1.2f;
            case "anio", "tributo", "periodo" -> 1.0f;
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
            case "anio" -> "Año";
            case "codigoPredio" -> "Predio";
            case "zona" -> "Zona";
            case "tributo" -> "Tributo";
            case "periodo" -> "Cuota";
            case "fechaVencimiento" -> "Vencimiento";
            case "montoSinBeneficio" -> "Sin Beneficio (S/)";
            case "montoConBeneficio" -> "Con Beneficio (S/)";
            default -> columna;
        };
    }

    public static Object obtenerValor(Object fila, String columna) {
        if (fila instanceof FilaDetalle f) {
            DetalleDeuda d = f.detalle();
            return switch (columna) {
                case "codigo" -> f.codigo();
                case "razonSocial" -> f.razonSocial();
                case "anio" -> d.anio();
                case "codigoPredio" -> d.codigoPredio();
                case "zona" -> d.zona();
                case "tributo" -> d.tributo() != null ? d.tributo().name() : "";
                case "periodo" -> d.periodo();
                case "fechaVencimiento" -> d.fechaVencimiento() != null ? d.fechaVencimiento().toString() : "";
                case "diasVencido" -> d.diasVencido();
                case "etapaCobranza" -> d.etapaCobranza() != null ? d.etapaCobranza().name() : "";
                case "montoSinBeneficio" -> d.montoSinBeneficio();
                case "montoConBeneficio" -> d.montoConBeneficio();
                default -> "";
            };
        } else if (fila instanceof FilaMoroso m) {
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
                case "detalleDeuda" -> m.detalleDeuda() != null
                        ? m.detalleDeuda().stream()
                                .map(d -> "Año " + d.anio() + " - " + d.tributo() + " - cuota " + d.periodo()
                                        + (d.codigoPredio() != null ? " - predio " + d.codigoPredio() : "")
                                        + " - " + d.diasVencido() + " días vencido"
                                        + (d.etapaCobranza() != null ? " - " + d.etapaCobranza() : "")
                                        + ": sin S/ " + d.montoSinBeneficio() + ", con S/ " + d.montoConBeneficio())
                                .collect(Collectors.joining("; "))
                        : "";
                default -> "";
            };
        } else if (fila instanceof ReportePricosRespuesta.FilaPricos p) {
            return switch (columna) {
                case "codigo" -> p.codigo();
                case "nombre", "razonSocial" -> p.razonSocial();
                case "regimen" -> p.regimen();
                case "tipoPersona" -> p.tipoPersona();
                case "tipoDocumento" -> p.documento() != null ? p.documento().tipo() : "";
                case "numeroDocumento" -> p.documento() != null ? p.documento().numero() : "";
                case "domicilioFiscal" -> p.domicilioFiscal() != null ? p.domicilioFiscal().direccion() : "";
                case "via" -> p.domicilioFiscal() != null ? p.domicilioFiscal().via() : "";
                case "numero" -> p.domicilioFiscal() != null ? p.domicilioFiscal().numero() : "";
                case "departamento" -> p.domicilioFiscal() != null ? p.domicilioFiscal().departamento() : "";
                case "interior" -> p.domicilioFiscal() != null ? p.domicilioFiscal().interior() : "";
                case "manzana" -> p.domicilioFiscal() != null ? p.domicilioFiscal().manzana() : "";
                case "lote" -> p.domicilioFiscal() != null ? p.domicilioFiscal().lote() : "";
                case "distrito" -> p.domicilioFiscal() != null ? p.domicilioFiscal().distrito() : "";
                case "baseImponible" -> p.baseImponible();
                case "emisionPredial" -> p.emisionAnioCorte() != null ? p.emisionAnioCorte().predial() : null;
                case "emisionArbitrios" -> p.emisionAnioCorte() != null ? p.emisionAnioCorte().arbitrios() : null;
                case "deudaAnteriorPredial" -> p.deudaAniosAnteriores() != null ? p.deudaAniosAnteriores().predial() : null;
                case "deudaAnteriorArbitrios" -> p.deudaAniosAnteriores() != null ? p.deudaAniosAnteriores().arbitrios() : null;
                case "deudaActualPredial" -> p.deudaAnioCorte() != null ? p.deudaAnioCorte().predial() : null;
                case "deudaActualArbitrios" -> p.deudaAnioCorte() != null ? p.deudaAnioCorte().arbitrios() : null;
                case "porcentajeDescuentoArbitrios" -> p.deudaAnioCorte() != null ? p.deudaAnioCorte().porcentajeDescuentoArbitrios() : null;
                case "condicion" -> p.condicion();
                case "correo" -> p.correo();
                case "telefonos" -> p.telefonos();
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
        if (filtros.anioDesde() != null && filtros.anioHasta() != null
                && !filtros.anioDesde().equals(filtros.anioHasta())) {
            partes.add("Años: " + filtros.anioDesde() + " - " + filtros.anioHasta());
        } else if (filtros.anio() != null) {
            partes.add("Año: " + filtros.anio());
        } else if (filtros.anioDesde() != null) {
            partes.add("Año: " + filtros.anioDesde());
        }
        if (filtros.zona() != null) partes.add("Zona: " + filtros.zona());
        if (filtros.tributo() != null) partes.add("Tributo: " + filtros.tributo());
        if (filtros.beneficio() != null && filtros.beneficio() != FiltroBeneficio.AMBOS) {
            partes.add("Beneficio: " + filtros.beneficio());
        }
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

