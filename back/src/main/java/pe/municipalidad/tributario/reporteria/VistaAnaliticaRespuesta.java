package pe.municipalidad.tributario.reporteria;

import java.time.LocalDateTime;
import java.util.Map;

public record VistaAnaliticaRespuesta(Long idVista, String nombre, Map<String, Object> estado,
        LocalDateTime fechaActualizacion) {
}
