package pe.municipalidad.tributario.reporte;

/*
 * Contenido binario y metadatos de un archivo exportado (Excel o PDF).
 */
public record ExportacionReporte(
        byte[] contenido,
        String nombreArchivo,
        String mediaType) {
}

