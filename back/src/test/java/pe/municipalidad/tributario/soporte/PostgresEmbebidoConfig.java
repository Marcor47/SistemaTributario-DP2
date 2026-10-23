package pe.municipalidad.tributario.soporte;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.municipalidad.tributario.config.RelojConfig;

/*
 * Los tests corren contra un PostgreSQL real embebido, para que las
 * migraciones de Flyway y la validación de Hibernate sean las mismas que en desarrollo.
 * Se levanta una sola instancia por ejecución y la comparten todos los contextos de Spring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresEmbebidoConfig {

    /* Fecha fija de los tests: coincide con la fecha de corte de los mocks de frontweb. */
    public static final LocalDate FECHA_PRUEBA = LocalDate.of(2026, 10, 19);

    private static EmbeddedPostgres postgres;

    @Bean
    public DataSource dataSource() {
        return obtenerPostgres().getPostgresDatabase();
    }

    @Bean
    @Primary
    public Clock relojFijo() {
        ZonedDateTime mediodia = ZonedDateTime.of(FECHA_PRUEBA, LocalTime.NOON, RelojConfig.ZONA_LIMA);
        return Clock.fixed(mediodia.toInstant(), RelojConfig.ZONA_LIMA);
    }

    private static synchronized EmbeddedPostgres obtenerPostgres() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException ex) {
                throw new UncheckedIOException("No se pudo iniciar el PostgreSQL embebido", ex);
            }
        }
        return postgres;
    }
}
