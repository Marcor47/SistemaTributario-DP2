package pe.municipalidad.tributario.integracion.firmador;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/*
 * Firma electrónica de Servicios en Línea con el mismo contrato de FirmadorGateway, mientras no se integre el
 * firmador digital del Estado (RNF-015). Reproduce sus rechazos: un certificado con la marca VENCIDO, REVOCADO o
 * NO_ACREDITADO se trata como el caso real equivalente.
 */
@Component
public class FirmadorServiciosEnLineaGateway implements FirmadorGateway {

    private static final String ENTIDAD = "Firma electrónica de Servicios en Línea - Municipalidad Distrital de San Miguel";
    private static final String MARCA_VENCIDO = "VENCIDO";
    private static final String MARCA_REVOCADO = "REVOCADO";
    private static final String MARCA_NO_ACREDITADO = "NO_ACREDITADO";

    @Override
    public ResultadoFirma verificarFirma(byte[] documento, byte[] firma, String certificado) {
        String hash = sha256(documento);
        String motivo = motivoRechazo(firma, certificado);
        return new ResultadoFirma(motivo == null, ENTIDAD, hash, motivo);
    }

    @Override
    public DocumentoFirmado firmarComoMunicipalidad(byte[] documento) {
        return new DocumentoFirmado(documento, new ResultadoFirma(true, ENTIDAD, sha256(documento), null));
    }

    @Override
    public boolean firmaLocal() {
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
