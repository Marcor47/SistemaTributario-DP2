package pe.municipalidad.tributario.predios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/* HU002: predio de SAMNET con sus titulares. baseImponible es el autovalúo vigente con que se calcula el predial. */
public record PredioRespuesta(
        String codigoPredio,
        String numeroCatastral,
        String ubicacion,
        String usoPredio,
        String tipoPredio,
        BigDecimal areaTerreno,
        BigDecimal baseImponible,
        LocalDate fechaAdquisicion,
        String zona,
        List<Titular> titulares) {

    public record Titular(String codigo, String nombre, String tipoDocumento, String numeroDocumento,
            String condicionPropiedad, BigDecimal porcentajePropiedad) {
    }
}
