package pe.municipalidad.tributario.reporte;

import jakarta.validation.constraints.Size;
import java.util.List;

/*
 * Petición para actualizar una vista personalizada de reporte (HU007, RF-008).
 */
public record ActualizarVistaPeticion(
        @Size(max = 80, message = "El nombre de la vista no puede exceder 80 caracteres.")
        String nombre,

        TipoGrafica tipoGrafica,

        FiltrosReporte filtros,

        List<String> columnasVisibles,

        List<String> columnasDetalleVisibles,

        List<String> ordenColumnas,

        List<String> ordenColumnasDetalle,

        List<String> series,

        @Size(max = 250, message = "El criterio de orden no puede exceder 250 caracteres.")
        String orden) {
}

