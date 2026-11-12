package pe.municipalidad.tributario.constancia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.EstadoContribuyente;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;

/* HU020 / HU042: la constancia generada reproduce el formato institucional de la constancia de referencia. */
class GeneradorConstanciaPdfTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 11, 10, 30);

    private final GeneradorConstanciaPdf generador = new GeneradorConstanciaPdf();
    private final Contribuyente contribuyente = new Contribuyente("014502", TipoDocumento.DNI, "41028765",
            "GARCÍA TORRES, MARÍA ELENA", "GENERAL", TipoPersona.NATURAL, "HABIDO", "maria@example.org",
            "999888777", EstadoContribuyente.ACTIVO, null);

    @Test
    void reproduceElFormatoInstitucionalConLosDatosDeLaConstancia() throws Exception {
        byte[] pdf = generador.generar("CNA-2026-000123", contribuyente, FECHA, "PRD-001, PRD-002",
                "2024 - 2026", "Ventanilla de Pruebas", true);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains(
                    "Municipalidad de San Miguel",
                    "DOCUMENTO DE PRUEBA",
                    "Constancia de No Adeudo",
                    "Referencia: CNA-2026-000123",
                    "GARCÍA TORRES, MARÍA ELENA",
                    "Documento: DNI 41028765",
                    "Código de contribuyente: 014502",
                    "Fecha de emisión: 11/11/2026",
                    "Predios comprendidos: PRD-001, PRD-002",
                    "Período cubierto: 2024 - 2026",
                    "Atendido en ventanilla por: Ventanilla de Pruebas",
                    "a la fecha de emisión",
                    "Firma municipal simulada",
                    "1 / 1");
        }
    }

    @Test
    void citaLaFechaDePresentacionAunqueLaEmisionSeaPosterior() throws Exception {
        byte[] pdf = generador.generar("CNA-2026-000127", contribuyente, LocalDateTime.of(2026, 11, 20, 9, 0),
                LocalDate.of(2026, 11, 10), null, null, null, true);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documento))
                    .contains("a la fecha de presentación de la solicitud (10/11/2026)");
        }
    }

    @Test
    void enModoOficialUsaElTituloYNoMuestraLaAdvertenciaDePrueba() throws Exception {
        byte[] pdf = generador.generar("CNA-2026-000124", contribuyente, FECHA, false);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("CONSTANCIA DE NO ADEUDO", "No registra deuda tributaria pendiente");
            assertThat(texto).doesNotContain("Firma municipal simulada");
        }
    }

    @Test
    void distribuyeElContenidoLargoEnVariasPaginasNumeradas() throws Exception {
        String predios = "PRD-014502-01 ".repeat(300);
        byte[] pdf = generador.generar("CNA-2026-000125", contribuyente, FECHA, predios, null, null, true);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int paginas = documento.getNumberOfPages();
            assertThat(paginas).isGreaterThanOrEqualTo(2);
            assertThat(new PDFTextStripper().getText(documento)).contains(paginas + " / " + paginas);
        }
    }

    @Test
    void convierteCaracteresNoSoportadosSinRomperLaEmision() throws Exception {
        byte[] pdf = generador.generar("CNA-2026-000126", contribuyente, FECHA,
                "Av. La Marina — 1450 😀", null, null, true);

        assertThat(pdf).isNotEmpty();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documento)).contains("Av. La Marina - 1450");
        }
    }
}
