package pe.municipalidad.tributario;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pe.municipalidad.tributario.config.RelojConfig;

@SpringBootApplication
public class TributarioBackApplication {

	public static void main(String[] args) {
		/* Todas las fechas y horas del sistema son de Lima (UTC-5), sin importar la zona del servidor. */
		TimeZone.setDefault(TimeZone.getTimeZone(RelojConfig.ZONA_LIMA));
		SpringApplication.run(TributarioBackApplication.class, args);
	}

}
