package pe.municipalidad.tributario.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Toda fecha "de hoy" se obtiene de este Clock y no de LocalDate.now() directo,
 * así los tests pueden fijar la fecha y los cálculos de vencimiento son reproducibles.
 */
@Configuration
public class RelojConfig {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Bean
    public Clock reloj() {
        return Clock.system(ZONA_LIMA);
    }
}
