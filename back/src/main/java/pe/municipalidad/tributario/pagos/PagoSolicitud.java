package pe.municipalidad.tributario.pagos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PagoSolicitud(@NotEmpty List<@NotNull Long> obligaciones) {
}
