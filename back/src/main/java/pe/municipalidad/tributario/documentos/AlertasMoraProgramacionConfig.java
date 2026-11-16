package pe.municipalidad.tributario.documentos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Aísla el despacho SMTP para que no bloquee la programación del ETL. */
@Configuration
public class AlertasMoraProgramacionConfig {
    @Bean("alertasMoraScheduler")
    ThreadPoolTaskScheduler alertasMoraScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("alertas-mora-");
        return scheduler;
    }
}
