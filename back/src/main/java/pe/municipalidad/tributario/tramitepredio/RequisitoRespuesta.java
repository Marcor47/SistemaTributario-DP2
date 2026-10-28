package pe.municipalidad.tributario.tramitepredio;

public record RequisitoRespuesta(Long id, String nombre, boolean obligatorio) {
    public static RequisitoRespuesta desde(RequisitoDocumental requisito) {
        return new RequisitoRespuesta(requisito.getIdRequisito(), requisito.getNombreDocumento(),
                requisito.isObligatorio());
    }
}
