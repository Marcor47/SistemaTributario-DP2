package pe.municipalidad.tributario.formularios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.documentos.PlantillasInstitucionales;

class PlantillasInstitucionalesInicialesTest {

    private final PlantillasInstitucionales plantillas = new PlantillasInstitucionales();

    @Test
    void reemplazaRellenoYBorradoresDePrueba() throws IOException {
        assertTrue(PlantillasInstitucionalesIniciales.sinFormatoInstitucional(new byte[10]));
        assertTrue(PlantillasInstitucionalesIniciales.sinFormatoInstitucional(word("DOCUMENTO DE PRUEBA", null)));
        assertTrue(PlantillasInstitucionalesIniciales.sinFormatoInstitucional(
                word("Declaración Jurada de Predio", "PLATAFORMA ANALÍTICA SAMNET | DEMOSTRACIÓN")));
    }

    @Test
    void respetaLasPlantillasInstitucionalesYLosPdf() throws IOException {
        for (String codigo : java.util.List.of("PL-001", "FO-001", "FO-002", "FO-003")) {
            byte[] generado = plantillas.generar(plantillas.formato(codigo).orElseThrow());
            assertFalse(PlantillasInstitucionalesIniciales.sinFormatoInstitucional(generado), codigo);
        }
        assertFalse(PlantillasInstitucionalesIniciales.sinFormatoInstitucional(word("Formato propio de la Gerencia", null)));
    }

    private static byte[] word(String cuerpo, String encabezado) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (encabezado != null) {
                doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText(encabezado);
            }
            for (int i = 0; i < 200; i++) {
                doc.createParagraph().createRun().setText(cuerpo + " " + java.util.UUID.randomUUID());
            }
            doc.write(salida);
            return salida.toByteArray();
        }
    }
}
