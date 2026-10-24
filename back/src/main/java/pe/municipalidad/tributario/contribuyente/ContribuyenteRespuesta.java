package pe.municipalidad.tributario.contribuyente;

public record ContribuyenteRespuesta(
        String codigo,
        String nombre,
        TipoPersona tipoPersona,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String domicilioFiscal,
        EstadoContribuyente estado) {

    public static ContribuyenteRespuesta desde(Contribuyente contribuyente) {
        return new ContribuyenteRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getTipoPersona(),
                contribuyente.getTipoDocumento(),
                contribuyente.getNumeroDocumento(),
                contribuyente.getDomicilioFiscal().direccion(),
                contribuyente.getEstado());
    }
}
