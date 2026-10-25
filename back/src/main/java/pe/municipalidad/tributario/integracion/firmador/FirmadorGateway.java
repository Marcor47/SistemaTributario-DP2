package pe.municipalidad.tributario.integracion.firmador;

/*
 * Verificación de la firma del contribuyente y firma automática de la Municipalidad con el firmador digital
 * del Estado (RF-021, RF-037, RNF-007). Sin acceso al firmador, se usa FirmadorSimuladoGateway.
 */
public interface FirmadorGateway {

    ResultadoFirma verificarFirma(byte[] documento, byte[] firma, String certificado);

    DocumentoFirmado firmarComoMunicipalidad(byte[] documento);

    /* Los documentos firmados con un servicio simulado llevan la marca "DOCUMENTO DE PRUEBA" (RNF-015). */
    boolean esSimulado();

    /* motivoRechazo explica al contribuyente por qué la firma no es válida (HU015); es nulo si es válida. */
    record ResultadoFirma(boolean valida, String entidadCertificadora, String hashDocumento, String motivoRechazo) {
    }

    record DocumentoFirmado(byte[] contenido, ResultadoFirma firma) {
    }
}
