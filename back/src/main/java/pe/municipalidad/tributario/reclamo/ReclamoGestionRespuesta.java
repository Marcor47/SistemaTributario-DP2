package pe.municipalidad.tributario.reclamo;

public record ReclamoGestionRespuesta(
        ReclamoRespuesta reclamo,
        String reclamante,
        Long idAreaActual,
        String areaActual,
        Long idResponsableActual,
        String responsableActual) {

    public static ReclamoGestionRespuesta desde(Reclamo reclamo) {
        String reclamante = reclamo.getTipoReclamante() == TipoReclamante.CONTRIBUYENTE
                ? reclamo.getDocumentoContribuyente()
                : reclamo.getUsuarioReclamante().getNombreCompleto();
        return new ReclamoGestionRespuesta(ReclamoRespuesta.desde(reclamo), reclamante,
                reclamo.getAreaActual() == null ? null : reclamo.getAreaActual().getIdArea(),
                reclamo.getAreaActual() == null ? null : reclamo.getAreaActual().getNombre(),
                reclamo.getResponsableActual() == null ? null : reclamo.getResponsableActual().getIdUsuario(),
                reclamo.getResponsableActual() == null ? null : reclamo.getResponsableActual().getNombreCompleto());
    }
}
