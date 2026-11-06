package pe.municipalidad.tributario.integracion.niubiz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

class NiubizSandboxGatewayTest {
    private static final String API = "https://apisandbox.vnforappstest.com";
    private MockRestServiceServer servidor;
    private NiubizSandboxGateway gateway;

    @BeforeEach
    void configurar() {
        var builder = RestClient.builder().baseUrl(API);
        servidor = MockRestServiceServer.bindTo(builder).build();
        gateway = new NiubizSandboxGateway("user", "pass", "123456789", "https://app.example/callback",
                "https://app.example/timeout", builder.build());
    }

    @Test
    void creaCheckoutYAutorizaSoloImporteCoincidente() {
        seguridad();
        servidor.expect(requestTo(API + "/api.ecommerce/v2/ecommerce/token/session/123456789"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "security-token"))
                .andExpect(jsonPath("$.antifraud.clientIp").value("192.0.2.1"))
                .andExpect(jsonPath("$.antifraud.merchantDefineData.MDD4").value("persona@example.com"))
                .andRespond(withSuccess("{\"sessionKey\":\"checkout-session\"}", MediaType.APPLICATION_JSON));
        seguridad(); autorizacion("25.50");
        seguridad(); autorizacion("24.50");
        Contribuyente contribuyente = mock(Contribuyente.class);
        when(contribuyente.getCorreo()).thenReturn("persona@example.com");
        when(contribuyente.getCodigoContribuyente()).thenReturn("021390");
        var orden = gateway.crearOrdenPago(new BigDecimal("25.50"), "OBLIGACIONES-47", contribuyente, "192.0.2.1");
        assertThat(orden.idOrden()).isEqualTo("000000000047");
        assertThat(orden.checkout().sessionKey()).isEqualTo("checkout-session");
        assertThat(gateway.autorizar(orden.idOrden(), new BigDecimal("25.50"), "token-12345678").autorizado()).isTrue();
        assertThat(gateway.autorizar(orden.idOrden(), new BigDecimal("25.50"), "token-12345678").autorizado()).isFalse();
        servidor.verify();
    }

    @Test
    void exigeTokenYCorreo() {
        assertThatThrownBy(() -> gateway.autorizar("000000000047", new BigDecimal("25.50"), null))
                .isInstanceOf(DatoInvalidoException.class);
        assertThatThrownBy(() -> gateway.crearOrdenPago(new BigDecimal("25.50"), "OBLIGACIONES-47", mock(Contribuyente.class), "192.0.2.1"))
                .hasMessageContaining("correo");
        servidor.verify();
    }

    @Test
    void springConstruyeElProveedorSandboxConLasPropiedadesConfiguradas() {
        new ApplicationContextRunner().withUserConfiguration(NiubizSandboxGateway.class)
                .withPropertyValues("tributario.niubiz.mode=sandbox", "tributario.niubiz.username=user",
                        "tributario.niubiz.password=pass", "tributario.niubiz.merchant-id=123456789",
                        "tributario.niubiz.callback-url=https://app.example/callback",
                        "tributario.niubiz.timeout-url=https://app.example/timeout")
                .run(contexto -> assertThat(contexto).hasSingleBean(NiubizSandboxGateway.class));
    }

    private void seguridad() {
        servidor.expect(requestTo(API + "/api.security/v1/security")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic dXNlcjpwYXNz"))
                .andRespond(withSuccess("security-token", MediaType.TEXT_PLAIN));
    }

    @Test
    void sinCredencialesElContextoArrancaPeroLosPagosQuedanBloqueados() {
        new ApplicationContextRunner().withUserConfiguration(NiubizSandboxGateway.class)
                .withPropertyValues("tributario.niubiz.mode=sandbox")
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(NiubizSandboxGateway.class);
                    var incompleto = contexto.getBean(NiubizSandboxGateway.class);
                    assertThatThrownBy(() -> incompleto.crearOrdenPago(new BigDecimal("25.50"),
                            "OBLIGACIONES-47", mock(Contribuyente.class), "192.0.2.1"))
                            .isInstanceOf(NiubizNoConfiguradoException.class);
                    assertThatThrownBy(() -> incompleto.autorizar("000000000047", new BigDecimal("25.50"), "token"))
                            .isInstanceOf(NiubizNoConfiguradoException.class);
                });
    }

    @Test
    void configuracionInvalidaSeRechazaAntesDeContactarNiubiz() {
        var httpSinUso = mock(RestClient.class);
        var incompleto = new NiubizSandboxGateway("user", "pass", "123456789", "http://app.example/retorno",
                "https://app.example/timeout", httpSinUso);
        assertThatThrownBy(() -> incompleto.autorizar("000000000047", new BigDecimal("25.50"), "token"))
                .isInstanceOf(NiubizNoConfiguradoException.class);
        verifyNoInteractions(httpSinUso);
    }
    private void autorizacion(String monto) {
        servidor.expect(requestTo(API + "/api.authorization/v3/authorization/ecommerce/123456789"))
                .andExpect(jsonPath("$.order.purchaseNumber").value("000000000047"))
                .andExpect(jsonPath("$.order.tokenId").value("token-12345678"))
                .andExpect(jsonPath("$.order.amount").value(25.5))
                .andRespond(withSuccess("{\"dataMap\":{\"STATUS\":\"Authorized\",\"ACTION_CODE\":\"000\",\"AMOUNT\":\""
                        + monto + "\",\"AUTHORIZATION_CODE\":\"ABC123\"}}", MediaType.APPLICATION_JSON));
    }
}
