package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SeguridadWebConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, UsuarioInternoFilter filtro, SeguridadEventoService eventos)
            throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http.cors(Customizer.withDefaults())
                .csrf(config -> config.csrfTokenRepository(csrf).requireCsrfProtectionMatcher(request -> {
                    String metodo = request.getMethod();
                    if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)) return false;
                    String ruta = request.getRequestURI();
                    if (!ruta.startsWith("/api/")) return false;
                    if (ruta.startsWith("/api/auth/") && !ruta.startsWith("/api/auth/interno/")) return false;
                    return !ruta.equals("/api/auth/interno/login")
                            && !ruta.startsWith("/api/portal/")
                            && !ruta.startsWith("/api/constancias/")
                            && !ruta.equals("/api/constancias");
                }))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/api/health", "/api/auth/**", "/api/portal/**", "/api/constancias/**",
                                "/api/estado-cuenta/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/usuarios").hasAuthority("USUARIOS:CREAR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/usuarios").hasAuthority("USUARIOS:LISTAR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/roles").hasAuthority("ROLES:LISTAR")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/usuarios/*/rol").hasAuthority("ROLES:CAMBIAR")
                        .requestMatchers(HttpMethod.POST, "/api/formularios").hasAuthority("FORMULARIOS:PUBLICAR")
                        .requestMatchers(HttpMethod.GET, "/api/formularios/**").hasAuthority("FORMULARIOS:CONSULTAR")
                        .requestMatchers(HttpMethod.POST, "/api/indicadores/morosidad/calculos")
                                .hasAuthority("INDICADORES:CALCULAR")
                        .requestMatchers(HttpMethod.GET, "/api/indicadores/**").hasAuthority("INDICADORES:CONSULTAR")
                        .requestMatchers("/api/metas/**").hasAuthority("METAS:GESTIONAR")
                        .requestMatchers(HttpMethod.POST, "/api/alertas-desvio/evaluaciones")
                                .hasAuthority("ALERTAS:EVALUAR")
                        .requestMatchers(HttpMethod.GET, "/api/alertas-desvio/**").hasAuthority("ALERTAS:CONSULTAR")
                        .requestMatchers("/api/etl/**").hasAuthority("ETL:GESTIONAR")
                        .requestMatchers("/api/regularidad/**", "/api/contribuyentes/*/regularidad")
                                .hasAuthority("REGULARIDAD:CONSULTAR")
                        .requestMatchers("/api/contribuyentes/**").hasAuthority("CONTRIBUYENTES:CONSULTAR")
                        .requestMatchers("/api/predios/*/declaracion-jurada").hasAuthority("DECLARACIONES:CONSULTAR")
                        .requestMatchers("/api/predios/*/arbitrios").hasAuthority("ARBITRIOS:CONSULTAR")
                        .requestMatchers("/api/predios/**").hasAuthority("PREDIOS:CONSULTAR")
                        .requestMatchers("/api/obligaciones/**").hasAuthority("COBRANZA:CONSULTAR")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, ex) -> escribirError(response, 401,
                                "SESION_REQUERIDA", "Inicie sesión como usuario interno."))
                        .accessDeniedHandler((request, response, ex) -> {
                            String usuario = request.getUserPrincipal() == null ? null
                                    : request.getUserPrincipal().getName();
                            eventos.accesoDenegado(request, usuario);
                            escribirError(response, 403, "ACCESO_DENEGADO",
                                    "No tiene permiso para realizar esta operación.");
                        }))
                .addFilterBefore(filtro, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private static void escribirError(HttpServletResponse response, int estado, String codigo, String mensaje)
            throws java.io.IOException {
        response.setStatus(estado);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje
                + "\",\"detalles\":[]}");
    }
}
