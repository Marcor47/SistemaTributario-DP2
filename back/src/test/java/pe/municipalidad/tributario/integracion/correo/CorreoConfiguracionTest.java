package pe.municipalidad.tributario.integracion.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@TestPropertySource(properties = "tributario.correo.modo=smtp")
class CorreoConfiguracionTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void enModoSmtpSoloExisteElGatewayReal() {
        Map<String, CorreoGateway> gateways = contexto.getBeansOfType(CorreoGateway.class);

        assertThat(gateways).hasSize(1);
        assertThat(gateways.values().iterator().next())
                .isInstanceOf(CorreoSmtpGateway.class)
                .matches(gateway -> !gateway.registraSinEnviar());
    }
}
