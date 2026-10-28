package pe.municipalidad.tributario.cobranza;

/*
 * Etapas de la cobranza de una deuda vencida, en el orden en que avanza (RF-014): las cuatro de la cobranza
 * inductiva y, al final, la deuda exigible en la vía coactiva, según la Municipalidad (24/10/2026).
 * El sistema solo informa la etapa registrada en SAMNET: no decide pasar una deuda de etapa ni gestiona el
 * procedimiento coactivo, que está fuera del alcance del proyecto.
 */
public enum EtapaCobranza {

    MORA_TEMPRANA("Mora temprana",
            "Venció el pago voluntario; se envían recordatorios de pago no vinculantes."),
    CAMPANA("Campaña persuasiva",
            "Se notifican cartas inductivas o estados de cuenta y se ofrecen fraccionamiento y beneficios vigentes."),
    VALOR_EMITIDO("Valor emitido",
            "Se emitió y notificó en el domicilio fiscal una orden de pago o una resolución de determinación o multa."),
    ESPERA_LEGAL("Espera legal",
            "Corren 20 días hábiles para pagar el valor o presentar un recurso de reclamación."),
    COACTIVA("Exigible en vía coactiva",
            "Venció la espera legal sin pago ni reclamación; la deuda es exigible en la vía coactiva.");

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
