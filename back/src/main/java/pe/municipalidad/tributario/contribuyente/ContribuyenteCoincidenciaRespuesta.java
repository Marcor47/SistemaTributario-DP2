package pe.municipalidad.tributario.contribuyente;

/*
 * HU001: coincidencia liviana del padrón para el buscador interno de la ficha. No incluye predios
 * ni estado de cuenta; esos datos se consultan después con el documento exacto.
 */
public record ContribuyenteCoincidenciaRespuesta(
        String codigo,
        String nombre,
        String tipoDoc,
        String doc) {

    public static ContribuyenteCoincidenciaRespuesta desde(Contribuyente contribuyente) {
        return new ContribuyenteCoincidenciaRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getTipoDocumento().name(),
                contribuyente.getNumeroDocumento());
    }
}
