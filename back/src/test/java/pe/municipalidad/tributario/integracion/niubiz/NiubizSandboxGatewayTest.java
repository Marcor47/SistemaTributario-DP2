package pe.municipalidad.tributario.integracion.niubiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

class NiubizSandboxGatewayTest {
    private HttpServer server;
    private NiubizSandboxGateway gateway;
    private final AtomicReference<String> sessionBody = new AtomicReference<>();
    private final AtomicReference<String> authorizationBody = new AtomicReference<>();
    private volatile String authorizationResponse = "{\"dataMap\":{\"STATUS\":\"Authorized\",\"ACTION_CODE\":\"000\",\"AMOUNT\":\"25.50\",\"AUTHORIZATION_CODE\":\"ABC123\"}}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api.security/v1/security", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Basic ");
            respond(exchange, "security-token", "text/plain");
        });
        server.createContext("/api.ecommerce/v2/ecommerce/token/session/123456789", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("security-token");
            sessionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"sessionKey\":\"checkout-session\",\"expirationTime\":123}", "application/json");
        });
        server.createContext("/api.authorization/v3/authorization/ecommerce/123456789", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("security-token");
            authorizationBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, authorizationResponse, "application/json");
        });
        server.start();
        gateway = new NiubizSandboxGateway("user", "pass", "123456789", "https://app.example/callback",
                "https://app.example/timeout", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void creaCheckoutYAutorizaSoloImporteCoincidente() {
        Contribuyente contribuyente = mock(Contribuyente.class);
        when(contribuyente.getCorreo()).thenReturn("persona@example.com");
        when(contribuyente.getCodigoContribuyente()).thenReturn("021390");
        var orden = gateway.crearOrdenPago(new BigDecimal("25.50"), "OBLIGACIONES-47", contribuyente, "192.0.2.1");
        assertThat(orden.idOrden()).isEqualTo("000000000047");
        assertThat(orden.checkout().sessionKey()).isEqualTo("checkout-session");
        assertThat(sessionBody.get()).contains("\"channel\":\"web\"", "\"clientIp\":\"192.0.2.1\"", "persona@example.com");
        var estado = gateway.autorizar(orden.idOrden(), new BigDecimal("25.50"), "token-12345678");
        assertThat(estado.autorizado()).isTrue();
        assertThat(estado.codigoAutorizacion()).isEqualTo("ABC123");
        assertThat(authorizationBody.get()).contains("\"purchaseNumber\":\"000000000047\"", "\"tokenId\":\"token-12345678\"");
        authorizationResponse = authorizationResponse.replace("25.50", "24.50");
        assertThat(gateway.autorizar(orden.idOrden(), new BigDecimal("25.50"), "token-87654321").autorizado()).isFalse();
    }

    @Test
    void exigeTokenYCorreo() {
        assertThatThrownBy(() -> gateway.autorizar("000000000047", new BigDecimal("25.50"), null))
                .isInstanceOf(DatoInvalidoException.class);
        Contribuyente contribuyente = mock(Contribuyente.class);
        assertThatThrownBy(() -> gateway.crearOrdenPago(new BigDecimal("25.50"), "OBLIGACIONES-47", contribuyente, "192.0.2.1"))
                .hasMessageContaining("correo");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body, String type) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
