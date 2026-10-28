package pe.municipalidad.tributario.constancia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * Cuerpo de POST /api/constancias/{id}/firma: lo que devuelve el firmador del Estado tras firmar el documento
 * de GET /api/constancias/{id}/documento-firma. El documento no se recibe: el servidor verifica la firma
 * contra el que él mismo generó (RNF-029).
 */
public record FirmaSolicitudConstancia(
        @NotBlank(message = "es obligatoria")
        @Size(max = 65536, message = "admite hasta 65536 caracteres")
        String firmaBase64,

        @NotBlank(message = "es obligatorio")
        @Size(max = 16384, message = "admite hasta 16384 caracteres")
        String certificado) {
}
