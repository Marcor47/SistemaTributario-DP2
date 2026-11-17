package pe.municipalidad.tributario.integracion.correo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/*
 * HU016: arma el asunto, el texto plano y el HTML institucional del recordatorio. El HTML se carga de
 * resources/correo/recordatorio-vencimiento.html y se rellenan solo los marcadores conocidos; los valores
 * del contribuyente se escapan para que no puedan inyectar etiquetas.
 */
@Component
public class RecordatorioCorreoHtml {

    public record Contenido(String asunto, String texto, String html) {
    }

    private static final Pattern MARCADOR = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    private static final String PLANTILLA_POR_DEFECTO = """
            <!doctype html>
            <html lang="es">
            <body style="margin:0;padding:24px;background:#f3f4f6;font-family:Arial,sans-serif;color:#1f2937">
              <div style="max-width:600px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;padding:28px">
                <h2 style="margin:0 0 16px;color:#0f3d6e">Recordatorio de vencimiento</h2>
                <p>Estimado(a) {{nombre}}:</p>
                <p>Su obligación N.° <strong>{{idObligacion}}</strong>{{detalleTributo}} vence el
                  <strong>{{fechaVencimiento}}</strong>.</p>
                <p>Saldo pendiente: <strong>S/ {{saldo}}</strong>.</p>
                <p><a href="{{portalUrl}}/estado-cuenta" style="display:inline-block;background:#0f3d6e;color:#ffffff;padding:10px 18px;border-radius:8px;text-decoration:none">Ver estado de cuenta</a></p>
                <p style="font-size:13px;color:#6b7280">Si no desea recibir este recordatorio, desactívelo en
                  Mis datos y notificaciones.</p>
              </div>
            </body>
            </html>
            """;

    private final String portalUrl;
    private final String plantilla;

    public RecordatorioCorreoHtml(
            @Value("${tributario.correo.portal-url:http://localhost:5173}") String portalUrl) {
        this.portalUrl = (portalUrl == null || portalUrl.isBlank())
                ? "http://localhost:5173"
                : portalUrl.strip().replaceAll("/+$", "");
        this.plantilla = cargarPlantilla();
    }

    public Contenido construir(String nombre, String idObligacion, String tributo,
            String fechaVencimiento, String saldo) {
        String nombreSeguro = normalizar(nombre, "contribuyente");
        String idSeguro = normalizar(idObligacion, "");
        String tributoSeguro = normalizar(tributo, "");
        String fechaSegura = normalizar(fechaVencimiento, "la fecha indicada");
        String saldoSeguro = normalizar(saldo, "0.00");
        String detalleTributo = tributoSeguro.isBlank() ? "" : " (" + tributoSeguro + ")";

        String asunto = "Recordatorio de vencimiento"
                + (tributoSeguro.isBlank() ? "" : ": " + tributoSeguro)
                + (idSeguro.isBlank() ? "" : " · Obligación N.° " + idSeguro);
        String texto = "Estimado(a) " + nombreSeguro + ":\n\n"
                + "Le recordamos que su obligación N.° " + idSeguro + detalleTributo
                + " vence el " + fechaSegura + ".\n"
                + "Saldo pendiente: S/ " + saldoSeguro + ".\n\n"
                + "Puede pagar en Servicios en Línea > Estado de cuenta.\n"
                + "Si no desea recibir este recordatorio, desactívelo en Servicios en Línea > "
                + "Mis datos y notificaciones.";

        Map<String, String> valores = new HashMap<>();
        valores.put("nombre", escapar(nombreSeguro));
        valores.put("idObligacion", escapar(idSeguro));
        valores.put("detalleTributo", escapar(detalleTributo));
        valores.put("fechaVencimiento", escapar(fechaSegura));
        valores.put("saldo", escapar(saldoSeguro));
        valores.put("portalUrl", escapar(portalUrl));

        return new Contenido(asunto, texto, aplicar(plantilla, valores));
    }

    private static String normalizar(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor.strip();
    }

    /* UTF-8 conserva las tildes y Ñ; solo se escapan los caracteres con significado HTML. */
    private static String escapar(String valor) {
        return HtmlUtils.htmlEscape(valor, StandardCharsets.UTF_8.name());
    }

    private static String aplicar(String plantilla, Map<String, String> valores) {
        Matcher matcher = MARCADOR.matcher(plantilla);
        StringBuilder salida = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(salida,
                    Matcher.quoteReplacement(valores.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(salida);
        return salida.toString();
    }

    private static String cargarPlantilla() {
        ClassPathResource recurso = new ClassPathResource("correo/recordatorio-vencimiento.html");
        if (!recurso.exists()) {
            return PLANTILLA_POR_DEFECTO;
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return PLANTILLA_POR_DEFECTO;
        }
    }
}
