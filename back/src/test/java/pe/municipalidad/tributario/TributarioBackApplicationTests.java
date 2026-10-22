package pe.municipalidad.tributario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.municipalidad.tributario.soporte.PostgresEmbebidoConfig;

@SpringBootTest
@Import(PostgresEmbebidoConfig.class)
class TributarioBackApplicationTests {

	@Test
	void contextLoads() {
	}

}
