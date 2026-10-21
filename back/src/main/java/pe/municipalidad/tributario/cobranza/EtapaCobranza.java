package pe.municipalidad.tributario.cobranza;

/*
 * Etapas de la cobranza de una deuda vencida, en el orden en que avanza.
 * El sistema solo informa la etapa registrada: no decide pasar una deuda a coactiva,
 * porque esa decisión está fuera del alcance del proyecto.
 */
public enum EtapaCobranza {

    ORDINARIA("Cobranza ordinaria",
            "Deuda vencida sin valor emitido; se gestionan recordatorios y avisos de cobranza."),
    VALOR_EMITIDO("Valor emitido",
            "Se emitió una orden de pago o resolución de determinación por la deuda."),
    VALOR_NOTIFICADO("Valor notificado",
            "El valor fue notificado al contribuyente; corre el plazo para pagar o reclamar."),
    COACTIVA("Cobranza coactiva",
            "El expediente fue derivado al Ejecutor Coactivo.");

    private final String nombre;
    private final String descripcion;

    EtapaCobranza(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
