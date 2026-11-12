package pe.municipalidad.tributario.etl;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/* Habilita la ejecución diaria del ETL (EtlService.ejecutarProgramado). Sin tributario.etl.programacion no corre. */
@Configuration
@EnableScheduling
public class EtlProgramacionConfig {
}
