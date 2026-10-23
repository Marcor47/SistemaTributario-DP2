package pe.municipalidad.tributario.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SeguridadWebConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean segura) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("SESSION");
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Strict");
        cookie.setUseSecureCookie(segura);
        return cookie;
    }

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(CookieSerializer cookieSerializer) {
        CookieHttpSessionIdResolver cookie = new CookieHttpSessionIdResolver();
        cookie.setCookieSerializer(cookieSerializer);
        return new CanalHttpSessionIdResolver(cookie, HeaderHttpSessionIdResolver.xAuthToken());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${tributario.seguridad.origenes-permitidos:http://localhost:5173,http://localhost:8081}")
            String origenes) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim)
                .filter(origen -> !origen.isEmpty()).toList());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Client-Platform", "X-Auth-Token",
                "X-Demo-Internal-Key"));
        configuracion.setExposedHeaders(List.of("X-Auth-Token"));
        configuracion.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    @Bean
    SecurityFilterChain seguridadApi(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        http.cors(Customizer.withDefaults())
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(csrfHandler))
                .httpBasic(config -> config.disable())
                .formLogin(config -> config.disable())
                .logout(config -> config.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private static final class CanalHttpSessionIdResolver implements HttpSessionIdResolver {
        private final HttpSessionIdResolver web;
        private final HttpSessionIdResolver movil;

        private CanalHttpSessionIdResolver(HttpSessionIdResolver web, HttpSessionIdResolver movil) {
            this.web = web;
            this.movil = movil;
        }

        @Override
        public List<String> resolveSessionIds(HttpServletRequest request) {
            return resolver(request).resolveSessionIds(request);
        }

        @Override
        public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
            resolver(request).setSessionId(request, response, sessionId);
        }

        @Override
        public void expireSession(HttpServletRequest request, HttpServletResponse response) {
            resolver(request).expireSession(request, response);
        }

        private HttpSessionIdResolver resolver(HttpServletRequest request) {
            return "mobile".equalsIgnoreCase(request.getHeader("X-Client-Platform")) ? movil : web;
        }
    }
}
