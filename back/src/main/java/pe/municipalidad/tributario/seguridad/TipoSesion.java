package pe.municipalidad.tributario.seguridad;

/* Origen de la sesión; define el límite de inactividad (RNF-022). */
public enum TipoSesion {

    INTERNA(15),
    CONTRIBUYENTE(10);

    private final int minutosInactividad;

    TipoSesion(int minutosInactividad) {
        this.minutosInactividad = minutosInactividad;
    }

    public int getMinutosInactividad() {
        return minutosInactividad;
    }
}
