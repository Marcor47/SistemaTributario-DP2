package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/*
 * Petición para emitir la Constancia de No Adeudo en Ventanilla (HU042, RF-055).
 * El trabajador ingresa el DNI/documento del contribuyente y, opcionalmente, predios y rango de fechas.
 */
public record EmitirConstanciaVentanillaSolicitud(
        @NotBlank(message = "El documento del contribuyente es obligatorio")
        @Size(max = 12, message = "El documento no puede superar 12 caracteres")
        String documentoContribuyente,

        List<String> predios,

        @Size(max = 400, message = "El texto de predios no puede exceder 400 caracteres")
        String prediosTexto,

        @Size(max = 60, message = "El rango de fechas no puede exceder 60 caracteres")
        String rangoFechas) {
}

