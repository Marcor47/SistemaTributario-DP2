package pe.municipalidad.tributario.obligacion;

/*
 * Servicios que financian los arbitrios de San Miguel; la ordenanza anual fija la tasa mensual de cada uno por uso y
 * zona del predio. Cada cuota de arbitrios guarda cuánto corresponde a cada servicio.
 */
public enum ServicioArbitrio {

    BARRIDO_CALLES("Barrido de calles"),
    RECOLECCION_RESIDUOS("Recolección de residuos sólidos"),
    PARQUES_JARDINES("Parques y jardines"),
    SERENAZGO("Serenazgo");

    private final String descripcion;

    ServicioArbitrio(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
