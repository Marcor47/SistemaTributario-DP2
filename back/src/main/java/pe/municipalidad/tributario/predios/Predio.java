package pe.municipalidad.tributario.predios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Predio(
        String codPredio,
        String catastral,
        String ubicacion,
        String estado,
        String uso,
        String tipo,
        String categoria,
        String giro,
        BigDecimal areaTerreno,
        LocalDate fechaAdquisicion,
        BigDecimal valorTerreno,
        BigDecimal valorNiveles,
        BigDecimal valorOtrasInstalaciones,
        BigDecimal baseImponible,
        String observaciones,
        List<Contribuyente> contribuyentes) {

    public record Contribuyente(String codigo, String nombre, String tipoDoc, String doc, BigDecimal pct) {}
}
