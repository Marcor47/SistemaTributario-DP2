package pe.municipalidad.tributario.reporte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/*
 * Petición para guardar una vista personalizada de reporte (HU007, RF-008).
 */
public record GuardarVistaPeticion(
        @NotBlank(message = "El nombre de la vista es obligatorio.")
        @Size(max = 80, message = "El nombre de la vista no puede exceder 80 caracteres.")
        String nombre,

        TipoGrafica tipoGrafica,

        FiltrosReporte filtros,

        @NotEmpty(message = "Debe indicar al menos una columna visible.")
        List<String> columnasVisibles,

        List<String> columnasDetalleVisibles,

        List<String> ordenColumnas,

        List<String> ordenColumnasDetalle,

        @Size(max = 250, message = "El criterio de orden no puede exceder 250 caracteres.")
        String orden) {
}

