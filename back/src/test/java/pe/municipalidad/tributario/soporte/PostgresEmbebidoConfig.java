package pe.municipalidad.tributario.soporte;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String BASE_COMUN = "postgres";

    private static EmbeddedPostgres postgres;

    /*
     * Por defecto todos los contextos usan la base "postgres". Un test que aplica semillas extra (p. ej. las de
     * datos-demo) declara tributario.test.base con otro nombre y recibe una base vacía propia; si compartiera la
     * común, los contextos creados después fallarían al validar Flyway y verían datos que no esperan.
     */
    @Bean
    public DataSource dataSource(@Value("${tributario.test.base:" + BASE_COMUN + "}") String base) {
        EmbeddedPostgres instancia = obtenerPostgres();
        if (BASE_COMUN.equals(base)) {
            return instancia.getPostgresDatabase();
        }
        crearBaseSiNoExiste(instancia, base);
        return instancia.getDatabase("postgres", base);
    }

    private static synchronized void crearBaseSiNoExiste(EmbeddedPostgres instancia, String base) {
        if (!base.matches("[a-z_]+")) {
            throw new IllegalArgumentException("Nombre de base de prueba no válido: " + base);
        }
        try (Connection conexion = instancia.getPostgresDatabase().getConnection();
                Statement sentencia = conexion.createStatement();
                ResultSet existe = sentencia.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = '" + base + "'")) {
            if (!existe.next()) {
                sentencia.execute("CREATE DATABASE " + base);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo crear la base de prueba " + base, ex);
        }
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
                postgres = EmbeddedPostgres.builder().setPGStartupWait(java.time.Duration.ofSeconds(30)).start();
            } catch (IOException ex) {
                throw new UncheckedIOException("No se pudo iniciar el PostgreSQL embebido", ex);
            }
        }
        return postgres;
    }
}
