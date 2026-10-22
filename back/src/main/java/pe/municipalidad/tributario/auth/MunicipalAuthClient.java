package pe.municipalidad.tributario.auth;

import java.util.Optional;

public interface MunicipalAuthClient {
    Optional<String> autenticar(String codigoContribuyente, String password);
}
