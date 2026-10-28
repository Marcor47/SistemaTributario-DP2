package pe.municipalidad.tributario.contribuyente;

/* HU001: datos del contribuyente junto con sus impuestos (revisión del Sprint 1). */
public record ContribuyenteRespuesta(
        String codigo,
        String nombre,
        TipoPersona tipoPersona,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String domicilioFiscal,
        EstadoContribuyente estado,
        ImpuestosRespuesta impuestos) {

    public static ContribuyenteRespuesta desde(Contribuyente contribuyente, ImpuestosRespuesta impuestos) {
        return new ContribuyenteRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getTipoPersona(),
                contribuyente.getTipoDocumento(),
                contribuyente.getNumeroDocumento(),
                contribuyente.getDomicilioFiscal().direccion(),
                contribuyente.getEstado(),
                impuestos);
    }
}
