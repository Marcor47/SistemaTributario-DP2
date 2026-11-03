package pe.municipalidad.tributario.soporte;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PruebaIntegracion
@Import(AutorizacionInternaMockMvcConfig.class)
public @interface PruebaInterna {
}
