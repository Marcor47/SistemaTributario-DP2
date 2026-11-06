package pe.municipalidad.tributario.reporteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/* Dimensiones de la carga publicada: dan el orden de las listas que devuelve la consulta de datos. */
public record CatalogoReporteriaRespuesta(
        LocalDate fechaCorte,
        String primerMes,
        BigDecimal umbralPrico,
        List<Opcion> zonas,
        List<Opcion> tributos,
        List<String> segmentos,
        List<String> usos,
        List<Opcion> canales) {

    public record Opcion(String codigo, String nombre) {
    }
}
