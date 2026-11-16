package pe.municipalidad.tributario.constancia;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/*
 * Petición de emisión de la Constancia de No Adeudo en ventanilla (HU042 por pasos). La solicitud ya existe:
 * el trabajador solo indica los predios comprendidos y el rango de fechas de la constancia (RF-055).
 */
public record EmitirConstanciaVentanillaSolicitud(
        @Size(max = 100, message = "No se pueden seleccionar más de 100 predios")
        List<@NotBlank(message = "El código de predio no puede estar vacío")
                @Size(max = 50, message = "El código de predio no puede exceder 50 caracteres") String> predios,

        @Valid
        LocalDate fechaDesde,

        @Valid
        LocalDate fechaHasta) {

    public static EmitirConstanciaVentanillaSolicitud valoresPorDefecto() {
        return new EmitirConstanciaVentanillaSolicitud(List.of(), null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Indique ambas fechas y asegúrese de que la fecha final no sea anterior a la inicial")
    public boolean isRangoFechasValido() {
        if (fechaDesde == null && fechaHasta == null) return true;
        return fechaDesde != null && fechaHasta != null && !fechaHasta.isBefore(fechaDesde);
    }
}
