package pe.municipalidad.tributario.soporte;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@TestConfiguration
public class AutorizacionInternaMockMvcConfig {
    private static final String[] PERMISOS = {
        "USUARIOS:LISTAR", "USUARIOS:CREAR", "ROLES:LISTAR", "ROLES:CAMBIAR",
        "FORMULARIOS:CONSULTAR", "FORMULARIOS:PUBLICAR", "CONTRIBUYENTES:CONSULTAR",
        "REGULARIDAD:CONSULTAR", "INDICADORES:CONSULTAR", "INDICADORES:CALCULAR",
        "METAS:GESTIONAR", "ALERTAS:CONSULTAR", "ALERTAS:EVALUAR", "PREDIOS:CONSULTAR",
        "DECLARACIONES:CONSULTAR", "CUENTAS:CONSULTAR", "COBRANZA:CONSULTAR",
        "ARBITRIOS:CONSULTAR", "ETL:GESTIONAR", "RECLAMOS:CREAR", "RECLAMOS:GESTIONAR",
        "RECLAMOS:ATENDER"
    };

    @Bean
    MockMvcBuilderCustomizer usuarioInternoPorDefecto() {
        var autoridades = Arrays.stream(PERMISOS).map(SimpleGrantedAuthority::new).toList();
        return constructor -> constructor.defaultRequest(get("/").with(user("prueba-interna")
                .authorities(autoridades)));
    }
}
