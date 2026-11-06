package pe.municipalidad.tributario.reporteria;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * Cliente del servicio de lenguaje (API compatible con OpenAI). La dirección y la credencial llegan por las variables
 * LLM_URL y LLM_TOKEN. El servicio solo genera texto: nunca recibe credenciales de la base ni datos de identidad.
 */
@Component
public class ModeloLenguajeCliente {

    private static final Logger LOG = LoggerFactory.getLogger(ModeloLenguajeCliente.class);
    private static final String SIN_CONEXION =
            "El servicio de IA no responde: puede estar apagado o sin conexión.";

    public record Mensaje(String role, String content) {
    }

    public record Completado(String texto, String modelo) {
    }

    public record Salud(boolean disponible, String modelo, String mensaje) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json;
    private final String url;
    private final String token;

    public ModeloLenguajeCliente(JsonMapper json, @Value("${tributario.asistente.llm-url:}") String url,
            @Value("${tributario.asistente.llm-token:}") String token) {
        this.json = json;
        this.url = url == null ? "" : url.strip().replaceAll("/+$", "");
        this.token = token == null ? "" : token.strip();
    }

    public Salud salud() {
        if (url.isEmpty()) {
            return new Salud(false, null, "El servicio de IA no está configurado en este servidor.");
        }
        try {
            HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(URI.create(url + "/salud"))
                    .timeout(Duration.ofSeconds(4)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode cuerpo = json.readTree(respuesta.body());
            String estado = cuerpo.path("estado").asString("");
            String modelo = cuerpo.path("modelo").asString("");
            if (respuesta.statusCode() == 200 && "listo".equals(estado)) {
                return new Salud(true, modelo, null);
            }
            return new Salud(false, null, "cargando".equals(estado) ? "El modelo de IA se está cargando."
                    : "El servicio de IA no está listo.");
        } catch (IOException | IllegalArgumentException | JacksonException ex) {
            return new Salud(false, null, SIN_CONEXION);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Salud(false, null, SIN_CONEXION);
        }
    }

    public Completado completar(List<Mensaje> mensajes, int maximoTokens) {
        if (url.isEmpty()) {
            throw new ModeloLenguajeNoDisponibleException("El servicio de IA no está configurado en este servidor.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("messages", mensajes);
        cuerpo.put("max_tokens", maximoTokens);
        cuerpo.put("temperature", 0);
        try {
            HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(URI.create(url + "/v1/chat/completions"))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                LOG.warn("El servicio de lenguaje respondió HTTP {}", respuesta.statusCode());
                throw new ModeloLenguajeNoDisponibleException("El servicio de IA rechazó la consulta.");
            }
            JsonNode datos = json.readTree(respuesta.body());
            return new Completado(datos.path("choices").path(0).path("message").path("content").asString(""),
                    datos.path("model").asString(""));
        } catch (IOException | IllegalArgumentException | JacksonException ex) {
            throw new ModeloLenguajeNoDisponibleException(SIN_CONEXION);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModeloLenguajeNoDisponibleException(SIN_CONEXION);
        }
    }
}
