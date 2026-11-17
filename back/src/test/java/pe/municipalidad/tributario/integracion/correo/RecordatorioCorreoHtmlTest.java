package pe.municipalidad.tributario.integracion.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordatorioCorreoHtmlTest {

    private final RecordatorioCorreoHtml builder = new RecordatorioCorreoHtml("http://localhost:5173/");

    @Test
    void armaAsuntoTextoYHtmlConLosDatosDelRecordatorio() {
        RecordatorioCorreoHtml.Contenido contenido = builder.construir("María García", "OBL-7001",
                "Impuesto predial", "2026-11-15", "100.00");

        assertThat(contenido.asunto()).isEqualTo("Recordatorio de vencimiento: Impuesto predial");
        assertThat(contenido.texto()).contains("María García", "OBL-7001", "Impuesto predial",
                "2026-11-15", "100.00", "Mis datos y notificaciones");
        assertThat(contenido.html()).contains("María García", "OBL-7001", "Impuesto predial",
                "2026-11-15", "100.00", "http://localhost:5173/estado-cuenta");
    }

    @Test
    void escapaLosValoresDelContribuyenteEnElHtml() {
        RecordatorioCorreoHtml.Contenido contenido = builder.construir("<script>alert(1)</script>",
                "OBL-1", null, null, null);

        assertThat(contenido.html()).doesNotContain("<script>alert(1)</script>");
        assertThat(contenido.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(contenido.asunto()).isEqualTo("Recordatorio de vencimiento");
        assertThat(contenido.texto()).contains("<script>alert(1)</script>");
    }
}
