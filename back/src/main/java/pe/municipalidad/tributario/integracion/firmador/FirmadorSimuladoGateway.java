package pe.municipalidad.tributario.integracion.firmador;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/* Firma de prueba con el mismo contrato de FirmadorGateway; no tiene validez legal (RNF-015). */
@Component
public class FirmadorSimuladoGateway implements FirmadorGateway {

    private static final String ENTIDAD_SIMULADA = "FIRMADOR SIMULADO - DOCUMENTO DE PRUEBA";

    @Override
    public ResultadoFirma verificarFirma(byte[] documento, byte[] firma, String certificado) {
        boolean valida = firma != null && firma.length > 0 && certificado != null && !certificado.isBlank();
        return new ResultadoFirma(valida, ENTIDAD_SIMULADA, sha256(documento));
    }

    @Override
    public DocumentoFirmado firmarComoMunicipalidad(byte[] documento) {
        return new DocumentoFirmado(documento, new ResultadoFirma(true, ENTIDAD_SIMULADA, sha256(documento)));
    }

    @Override
    public boolean esSimulado() {
        return true;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
