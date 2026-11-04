package pe.municipalidad.tributario.integracion.niubiz;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/** Botón de Pago Web de Niubiz: seguridad, sesión de checkout y autorización servidor a servidor. */
@Component
@ConditionalOnProperty(name = "tributario.niubiz.mode", havingValue = "sandbox")
public class NiubizSandboxGateway implements NiubizGateway {
    private static final String API = "https://apisandbox.vnforappstest.com";
    private static final String CHECKOUT = "https://static-content-qas.vnforapps.com/env/sandbox/js/checkout.js";
    private final RestClient http;
    private final String username;
    private final String password;
    private final String merchantId;
    private final String callbackUrl;
    private final String timeoutUrl;

    @Autowired
    public NiubizSandboxGateway(@Value("${tributario.niubiz.username:}") String username,
            @Value("${tributario.niubiz.password:}") String password,
            @Value("${tributario.niubiz.merchant-id:}") String merchantId,
            @Value("${tributario.niubiz.callback-url:}") String callbackUrl,
            @Value("${tributario.niubiz.timeout-url:}") String timeoutUrl) {
        this(username, password, merchantId, callbackUrl, timeoutUrl, clienteHttp(API));
    }

    NiubizSandboxGateway(String username, String password, String merchantId, String callbackUrl,
            String timeoutUrl, RestClient http) {
        this.username = limpiar(username);
        this.password = limpiar(password);
        this.merchantId = limpiar(merchantId);
        this.callbackUrl = limpiar(callbackUrl);
        this.timeoutUrl = limpiar(timeoutUrl);
        this.http = http;
    }

    private static RestClient clienteHttp(String apiBaseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(apiBaseUrl).requestFactory(factory).build();
    }

    @Override
    public OrdenPago crearOrdenPago(BigDecimal monto, String referencia) {
        throw new IllegalStateException("El sandbox requiere los datos del contribuyente y su IP.");
    }

    @Override
    public OrdenPago crearOrdenPago(BigDecimal monto, String referencia, Contribuyente contribuyente, String clientIp) {
        validarConfiguracion();
        if (contribuyente.getCorreo() == null || contribuyente.getCorreo().isBlank()) {
            throw new ReglaNegocioException("CORREO_REQUERIDO_NIUBIZ", "Actualice su correo antes de pagar en línea.");
        }
        long id = Long.parseLong(referencia.substring(referencia.lastIndexOf('-') + 1));
        if (id > 999_999_999_999L) throw new IllegalStateException("Se agotó el número de orden de Niubiz.");
        String purchaseNumber = String.format("%012d", id);
        String token = seguridad();
        Map<String, Object> antifraud = Map.of("clientIp", clientIp,
                "merchantDefineData", Map.of("MDD4", contribuyente.getCorreo(),
                        "MDD32", contribuyente.getCodigoContribuyente(), "MDD75", "Registrado"));
        Map<?, ?> respuesta = enviarSesion(token, Map.of("channel", "web", "amount", monto,
                "antifraud", antifraud));
        String sessionKey = texto(respuesta.get("sessionKey"));
        if (sessionKey == null) throw new IllegalStateException("Niubiz no devolvió una sesión de checkout.");
        return new OrdenPago(purchaseNumber, null,
                new Checkout(sessionKey, merchantId, purchaseNumber, CHECKOUT, callbackUrl, timeoutUrl));
    }

    @Override
    public EstadoPagoPasarela consultarEstadoPago(String idOrden) {
        throw new DatoInvalidoException("TOKEN_NIUBIZ_REQUERIDO", "Envía el transactionToken del checkout de Niubiz.");
    }

    @Override
    public EstadoPagoPasarela autorizar(String idOrden, BigDecimal monto, String transactionToken) {
        validarConfiguracion();
        if (transactionToken == null || transactionToken.isBlank() || transactionToken.length() > 4096) {
            throw new DatoInvalidoException("TOKEN_NIUBIZ_REQUERIDO", "Envía un transactionToken válido del checkout de Niubiz.");
        }
        String token = seguridad();
        Map<String, Object> order = Map.of("tokenId", transactionToken, "purchaseNumber", idOrden,
                "amount", monto, "currency", "PEN");
        Map<?, ?> respuesta;
        try {
            respuesta = http.post().uri("/api.authorization/v3/authorization/ecommerce/{merchantId}", merchantId)
                    .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("channel", "web", "captureType", "manual", "countable", true, "order", order))
                    .retrieve().body(Map.class);
        } catch (RestClientException ex) {
            // Un timeout puede ocurrir tras el cobro. No se reintenta automáticamente la autorización.
            throw new NiubizNoDisponibleException(
                    "No se pudo determinar el resultado de Niubiz. Concilie la orden antes de reintentar.", ex);
        }
        if (respuesta == null || !(respuesta.get("dataMap") instanceof Map<?, ?> data)) {
            return new EstadoPagoPasarela(idOrden, false, null, null);
        }
        String status = texto(data.get("STATUS"));
        String action = texto(data.get("ACTION_CODE"));
        String amount = texto(data.get("AMOUNT"));
        BigDecimal autorizado;
        try { autorizado = amount == null ? null : new BigDecimal(amount); }
        catch (NumberFormatException ex) { autorizado = null; }
        String codigo = texto(data.get("AUTHORIZATION_CODE"));
        boolean ok = "Authorized".equalsIgnoreCase(status) && ("000".equals(action) || "010".equals(action))
                && autorizado != null && autorizado.compareTo(monto) == 0 && codigo != null && !codigo.isBlank();
        return new EstadoPagoPasarela(idOrden, ok, codigo, autorizado);
    }

    private String seguridad() {
        try {
            String token = http.get().uri("/api.security/v1/security")
                    .headers(h -> h.setBasicAuth(username, password)).retrieve().body(String.class);
            if (token == null || token.isBlank()) throw new IllegalStateException("Niubiz no devolvió token de seguridad.");
            return token.trim();
        } catch (RestClientException ex) {
            throw new NiubizNoDisponibleException("No se pudo autenticar con Niubiz.", ex);
        }
    }

    private Map<?, ?> enviarSesion(String token, Map<String, Object> body) {
        try {
            Map<?, ?> respuesta = http.post().uri("/api.ecommerce/v2/ecommerce/token/session/{merchantId}", merchantId)
                    .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            if (respuesta == null) throw new IllegalStateException("Niubiz no devolvió sesión.");
            return respuesta;
        } catch (RestClientException ex) {
            throw new NiubizNoDisponibleException("No se pudo crear la sesión de Niubiz.", ex);
        }
    }

    private static String texto(Object valor) { return valor == null ? null : valor.toString(); }
    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    /* Se valida antes de cualquier llamada externa, sin bloquear el arranque ni usar el simulador. */
    private void validarConfiguracion() {
        if (username.isBlank() || password.isBlank() || !merchantId.matches("\\d{9}")
                || !esHttps(callbackUrl) || !esHttps(timeoutUrl)) {
            throw new NiubizNoConfiguradoException();
        }
    }

    private static boolean esHttps(String valor) {
        try {
            URI uri = URI.create(valor);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
