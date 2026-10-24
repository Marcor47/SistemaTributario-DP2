package pe.municipalidad.tributario.integracion.autenticacion;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/*
 * Acepta una credencial de prueba configurada fuera del repositorio (RNF-015, RNF-025):
 * tributario.integracion.autenticacion-simulada.documento y .clave.
 */
@Component
@ConditionalOnProperty(name = "tributario.integracion.autenticacion", havingValue = "simulado")
public class AutenticacionSimuladaGateway implements AutenticacionMunicipalGateway {

    private final String documentoPrueba;
    private final String clavePrueba;

    public AutenticacionSimuladaGateway(
            @Value("${tributario.integracion.autenticacion-simulada.documento:}") String documentoPrueba,
            @Value("${tributario.integracion.autenticacion-simulada.clave:}") String clavePrueba) {
        this.documentoPrueba = documentoPrueba;
        this.clavePrueba = clavePrueba;
    }

    @Override
    public Optional<ContribuyenteAutenticado> autenticar(String documento, String clave) {
        if (documentoPrueba.isBlank() || clavePrueba.isBlank() || documento == null || clave == null) {
            return Optional.empty();
        }
        boolean valida = documentoPrueba.equals(documento) && MessageDigest.isEqual(
                clavePrueba.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8));
        return valida
                ? Optional.of(new ContribuyenteAutenticado("SIMULADO", "CONTRIBUYENTE DE PRUEBA"))
                : Optional.empty();
    }

    @Override
    public Optional<String> validarIdentidad(String dni) {
        return dni != null && dni.matches("\\d{8}") ? Optional.of("CIUDADANO DE PRUEBA") : Optional.empty();
    }
}
