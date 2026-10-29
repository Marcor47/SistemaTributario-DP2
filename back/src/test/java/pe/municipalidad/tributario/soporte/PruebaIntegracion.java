package pe.municipalidad.tributario.soporte;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/* Atajo para los tests de API: contexto completo, MockMvc y PostgreSQL embebido con fecha fija. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba")
@AutoConfigureMockMvc
@Import(PostgresEmbebidoConfig.class)
public @interface PruebaIntegracion {
}
