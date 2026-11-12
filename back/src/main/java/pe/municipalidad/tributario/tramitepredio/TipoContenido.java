package pe.municipalidad.tributario.tramitepredio;

/* Tipos de archivo permitidos en los adjuntos (RNF-031); se validan por contenido, no por extensión. */
public enum TipoContenido {
    PDF("application/pdf"),
    JPG("image/jpeg"),
    PNG("image/png");

    private final String tipoMime;

    TipoContenido(String tipoMime) {
        this.tipoMime = tipoMime;
    }

    public String tipoMime() {
        return tipoMime;
    }
}
