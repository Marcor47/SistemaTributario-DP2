package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.Size;
import java.util.List;

/*
 * Petición de emisión de la Constancia de No Adeudo en ventanilla (HU042 por pasos). La solicitud ya existe:
 * el trabajador solo indica los predios comprendidos y el rango de fechas de la constancia (RF-055).
 */
public record EmitirConstanciaVentanillaSolicitud(
        List<String> predios,

        @Size(max = 400, message = "El texto de predios no puede exceder 400 caracteres")
        String prediosTexto,

        @Size(max = 60, message = "El rango de fechas no puede exceder 60 caracteres")
        String rangoFechas) {
}
