package pe.municipalidad.tributario.reporteria;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RespuestaAsistente(
        String respuesta,
        String consulta,
        boolean consultaCorregida,
        List<String> columnas,
        List<List<Object>> filas,
        List<Map<String, Object>> graficos,
        LocalDate fechaCorte,
        String modelo,
        double segundos,
        boolean respuestaReemplazada) {
}
