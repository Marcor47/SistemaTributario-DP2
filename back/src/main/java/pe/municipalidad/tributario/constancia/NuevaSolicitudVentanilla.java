package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * Petición para crear la solicitud de constancia en ventanilla (HU042 por pasos). El contribuyente se identifica
 * por su DNI, RUC o código; el trabajador emisor lo toma la sesión interna.
 */
public record NuevaSolicitudVentanilla(
        @NotBlank(message = "El documento del contribuyente es obligatorio")
        @Size(max = 12, message = "El documento no puede superar 12 caracteres")
        String documentoContribuyente) {
}
