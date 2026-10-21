package pe.municipalidad.tributario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:contexttest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class TributarioBackApplicationTests {

	@Test
	void contextLoads() {
	}

}
