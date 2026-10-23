package pe.municipalidad.tributario;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import pe.municipalidad.tributario.auth.MunicipalAuthClient;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;

@SpringBootTest
@Import(PostgresEmbebidoConfig.class)
@TestPropertySource(properties = {
		"spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba,classpath:db/local"})
class TributarioBackApplicationTests {
	@Autowired ApplicationContext contexto;

	@Test
	void contextLoads() {
		assertTrue(contexto.getBeansOfType(MunicipalAuthClient.class).isEmpty());
	}

}
