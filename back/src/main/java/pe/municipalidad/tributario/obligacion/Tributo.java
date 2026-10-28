package pe.municipalidad.tributario.obligacion;

/*
 * Los arbitrios (limpieza pública, parques y jardines, seguridad ciudadana) se manejan como un solo tributo porque
 * no se conoce la fórmula con que la Municipalidad los distribuye; mostrarlos separados sería inventar montos.
 */
public enum Tributo {

    IMPUESTO_PREDIAL("Impuesto Predial"),
    ARBITRIOS("Arbitrios Municipales");

    private final String descripcion;

    Tributo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
