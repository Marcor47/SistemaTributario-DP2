package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.Size;

/* Cuerpo opcional de POST /api/constancias/{id}/orden-pago/confirmacion: el token que devuelve el checkout. */
public record ConfirmacionPagoConstancia(
        @Size(max = 200, message = "admite hasta 200 caracteres")
        String transactionToken) {
}
