package pe.municipalidad.tributario.reclamo;

public record AreaMunicipalRespuesta(Long idArea, String codigo, String nombre) {
    public static AreaMunicipalRespuesta desde(AreaMunicipal area) {
        return new AreaMunicipalRespuesta(area.getIdArea(), area.getCodigo(), area.getNombre());
    }
}
