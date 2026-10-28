package pe.municipalidad.tributario.integracion.firmador;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/*
 * Firma de prueba con el mismo contrato de FirmadorGateway; no tiene validez legal (RNF-015). Para demostrar
 * los rechazos sin el firmador del Estado, un certificado de prueba con la marca VENCIDO, REVOCADO o
 * NO_ACREDITADO se trata como el caso real equivalente.
 */
@Component
public class FirmadorSimuladoGateway implements FirmadorGateway {

    private static final String ENTIDAD_SIMULADA = "FIRMADOR SIMULADO - DOCUMENTO DE PRUEBA";
    private static final String MARCA_VENCIDO = "VENCIDO";
    private static final String MARCA_REVOCADO = "REVOCADO";
    private static final String MARCA_NO_ACREDITADO = "NO_ACREDITADO";

    @Override
    public ResultadoFirma verificarFirma(byte[] documento, byte[] firma, String certificado) {
        String hash = sha256(documento);
        String motivo = motivoRechazo(firma, certificado);
        return new ResultadoFirma(motivo == null, ENTIDAD_SIMULADA, hash, motivo);
    }

    @Override
    public DocumentoFirmado firmarComoMunicipalidad(byte[] documento) {
        return new DocumentoFirmado(documento, new ResultadoFirma(true, ENTIDAD_SIMULADA, sha256(documento), null));
    }

    @Override
    public boolean esSimulado() {
        return true;
    }

    private static String motivoRechazo(byte[] firma, String certificado) {
        if (firma == null || firma.length == 0) {
            return "no se recibió la firma del documento";
        }
        if (certificado == null || certificado.isBlank()) {
            return "no se recibió el certificado digital";
        }
        String marca = certificado.toUpperCase(Locale.ROOT);
        if (marca.contains(MARCA_NO_ACREDITADO)) {
            return "el certificado no fue emitido por una entidad acreditada ante la IOFE";
        }
        if (marca.contains(MARCA_REVOCADO)) {
            return "el certificado digital fue revocado";
        }
        if (marca.contains(MARCA_VENCIDO)) {
            return "el certificado digital está vencido";
        }
        return null;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
