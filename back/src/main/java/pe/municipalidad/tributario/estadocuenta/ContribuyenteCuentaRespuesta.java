package pe.municipalidad.tributario.estadocuenta;

import pe.municipalidad.tributario.contribuyente.Contribuyente;

/**
 * Subconjunto de datos del contribuyente que necesita la pantalla de Estado de Cuenta.
 * Usa "tipoDoc" y "doc" para coincidir con la nomenclatura del front (mocks/predios.js).
 * HU004.
 */
public record ContribuyenteCuentaRespuesta(
        String codigo,
        String nombre,
        String tipoDoc,
        String doc) {

    public static ContribuyenteCuentaRespuesta desde(Contribuyente c) {
        return new ContribuyenteCuentaRespuesta(
                c.getCodigoContribuyente(),
                c.getNombreRazonSocial(),
                c.getTipoDocumento().name(),
                c.getNumeroDocumento());
    }
}
