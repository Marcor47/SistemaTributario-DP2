package pe.municipalidad.tributario.integracion.autenticacion;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/* Consume ps_login y el servicio de consulta de DNI (ps_consultadni) de la capa municipal. */
@Component
@ConditionalOnProperty(name = "tributario.integracion.autenticacion", havingValue = "api", matchIfMissing = true)
public class AutenticacionMunicipalApiGateway implements AutenticacionMunicipalGateway {

    private final JdbcTemplate jdbc;

    public AutenticacionMunicipalApiGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ContribuyenteAutenticado> autenticar(String documento, String clave) {
        return jdbc.query("SELECT * FROM samnet.ps_login(?, ?)",
                (rs, i) -> new ContribuyenteAutenticado(rs.getString("contribuyente_codigo"), rs.getString("nombre")),
                documento, clave).stream().findFirst();
    }

    @Override
    public Optional<String> validarIdentidad(String dni) {
        return jdbc.query("SELECT nombre FROM samnet.ps_consultadni(?)", (rs, i) -> rs.getString("nombre"), dni)
                .stream().findFirst();
    }
}
