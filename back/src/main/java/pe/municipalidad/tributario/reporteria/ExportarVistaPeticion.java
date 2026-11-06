package pe.municipalidad.tributario.reporteria;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/*
 * Lo que la pantalla muestra al exportar: filtros, indicadores, datos de cada gráfico en su orden visible y el ranking
 * con sus columnas visibles. Así el archivo siempre coincide con la pantalla (RF-008).
 */
public record ExportarVistaPeticion(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "[a-z]{1,20}", message = "no es válido") String pestana,
        @NotBlank(message = "es obligatorio") @Size(max = 80, message = "es demasiado largo") String reporte,
        @NotBlank(message = "es obligatorio") @Size(max = 20, message = "es demasiado largo") String corte,
        @NotBlank(message = "es obligatorio") @Size(max = 400, message = "es demasiado largo") String filtros,
        @Size(max = 400, message = "es demasiado largo") String comparado,
        @Size(max = 200, message = "es demasiado largo") String resaltado,
        @NotNull(message = "es obligatorio") @Size(max = 12, message = "tiene demasiados elementos") List<List<String>> indicadores,
        @NotNull(message = "es obligatorio") @Size(max = 12, message = "tiene demasiados elementos") List<@Valid TablaExportada> graficos,
        @Valid TablaExportada ranking) {

    public record TablaExportada(
            @NotBlank(message = "es obligatorio") @Size(max = 160, message = "es demasiado largo") String titulo,
            @Size(max = 40, message = "es demasiado largo") String tipo,
            @NotNull(message = "es obligatorio") @Size(max = 40, message = "tiene demasiadas columnas") List<String> encabezados,
            @NotNull(message = "es obligatorio") @Size(max = 500, message = "tiene demasiadas filas") List<List<Object>> filas,
            @Size(max = 6_000_000, message = "la imagen es demasiado grande") String imagen) {
    }
}
