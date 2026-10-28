package pe.municipalidad.tributario.contribuyente;

import java.util.List;

/* HU001: datos del contribuyente junto con sus predios (revisión del Sprint 1). */
public record ContribuyenteRespuesta(
        String codigo,
        String nombre,
        TipoPersona tipoPersona,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String domicilioFiscal,
        EstadoContribuyente estado,
        List<PredioContribuyenteRespuesta> predios) {

    public static ContribuyenteRespuesta desde(Contribuyente contribuyente, List<PredioContribuyenteRespuesta> predios) {
        return new ContribuyenteRespuesta(
                contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(),
                contribuyente.getTipoPersona(),
                contribuyente.getTipoDocumento(),
                contribuyente.getNumeroDocumento(),
                contribuyente.getDomicilioFiscal().direccion(),
                contribuyente.getEstado(),
                predios);
    }
}
