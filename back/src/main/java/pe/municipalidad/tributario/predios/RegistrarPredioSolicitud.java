package pe.municipalidad.tributario.predios;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/*
 * HU036 (RF-048): datos para registrar un predio nuevo con su contribuyente titular.
 * condicionPropiedad (por defecto PROPIETARIO) y porcentajePropiedad (por defecto 100) son opcionales.
 */
public record RegistrarPredioSolicitud(
        @NotBlank(message = "es obligatorio") @Size(max = 10, message = "admite hasta 10 caracteres")
        String codigoContribuyente,
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9-]{1,12}$", message = "admite letras, números y guion, hasta 12 caracteres")
        String codigoPredio,
        @Size(max = 30, message = "admite hasta 30 caracteres") String numeroCatastral,
        @NotBlank(message = "es obligatoria") @Size(max = 250, message = "admite hasta 250 caracteres")
        String ubicacion,
        @Size(max = 60, message = "admite hasta 60 caracteres") String usoPredio,
        @NotNull(message = "es obligatorio") TipoPredio tipoPredio,
        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
        @DecimalMax(value = "99999999.99", message = "es demasiado grande") BigDecimal areaTerreno,
        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @DecimalMax(value = "999999999999.99", message = "es demasiado grande") BigDecimal baseImponible,
        @PastOrPresent(message = "no puede ser una fecha futura") LocalDate fechaAdquisicion,
        @NotBlank(message = "es obligatoria") @Size(max = 20, message = "admite hasta 20 caracteres") String zona,
        @Size(max = 30, message = "admite hasta 30 caracteres") String condicionPropiedad,
        @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
        @DecimalMax(value = "100", message = "no puede superar 100") BigDecimal porcentajePropiedad) {
}
