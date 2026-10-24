package pe.municipalidad.tributario.contribuyente;

/*
 * Value object del domicilio fiscal (RF-048, RF-024). direccion es la línea completa tal como la
 * registra SAMNET; los demás campos son su desglose.
 */
public record DomicilioFiscal(
        String direccion,
        String departamento,
        String provincia,
        String distrito,
        String via,
        String numero,
        String interior,
        String manzana,
        String lote) {
}
