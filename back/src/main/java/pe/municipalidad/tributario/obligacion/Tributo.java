package pe.municipalidad.tributario.obligacion;

public enum Tributo {

    IMPUESTO_PREDIAL("Impuesto Predial"),
    LIMPIEZA_PUBLICA("Limpieza Pública"),
    PARQUES_JARDINES("Parques y Jardines"),
    SEGURIDAD_CIUDADANA("Seguridad Ciudadana");

    private final String descripcion;

    Tributo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
