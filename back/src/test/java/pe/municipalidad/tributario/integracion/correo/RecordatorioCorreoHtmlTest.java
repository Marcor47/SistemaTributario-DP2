package pe.municipalidad.tributario.integracion.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordatorioCorreoHtmlTest {

    private final RecordatorioCorreoHtml builder = new RecordatorioCorreoHtml("http://localhost:5173/");

    @Test
    void armaAsuntoTextoYHtmlConLosDatosDelRecordatorio() {
        RecordatorioCorreoHtml.Contenido contenido = builder.construir("María García", "OBL-7001",
                "Impuesto predial", "2026-11-15", "100.00");

        assertThat(contenido.asunto())
                .isEqualTo("Recordatorio de vencimiento: Impuesto predial · Obligación N.° OBL-7001");
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
        assertThat(contenido.asunto())
                .isEqualTo("Recordatorio de vencimiento · Obligación N.° OBL-1");
        assertThat(contenido.texto()).contains("<script>alert(1)</script>");
    }

    @Test
    void elAsuntoDistingueCadaObligacionDelMismoTributo() {
        String primera = builder.construir("María García", "10", "Arbitrios", "2026-11-19", "80.00").asunto();
        String segunda = builder.construir("Prueba HU016", "11", "Arbitrios", "2026-11-19", "300.00").asunto();

        assertThat(primera).isEqualTo("Recordatorio de vencimiento: Arbitrios · Obligación N.° 10");
        assertThat(segunda).isEqualTo("Recordatorio de vencimiento: Arbitrios · Obligación N.° 11");
        assertThat(primera).isNotEqualTo(segunda);
    }
}
