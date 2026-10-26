package pe.municipalidad.tributario.auth;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.integracion.autenticacion.AutenticacionMunicipalGateway;

/** Autentica el documento y la clave contra samnet.ps_login en PostgreSQL. */
@Component
@Profile("!demo")
public class PostgresMunicipalAuthClient implements MunicipalAuthClient {
    private final AutenticacionMunicipalGateway autenticacion;

    public PostgresMunicipalAuthClient(AutenticacionMunicipalGateway autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Override
    public Optional<String> autenticar(String documento, String clave) {
        return autenticacion.autenticar(documento, clave)
                .map(AutenticacionMunicipalGateway.ContribuyenteAutenticado::codigoContribuyente);
    }
}
