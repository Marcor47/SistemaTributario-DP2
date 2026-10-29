package pe.municipalidad.tributario.contribuyente;

import java.math.BigDecimal;
import java.time.LocalDate;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;

/*
 * HU001 (revisión del Sprint 1): predio del que el contribuyente es titular, con su condición y porcentaje de
 * propiedad. En copropiedad cada titular ve su propia parte.
 */
public record PredioContribuyenteRespuesta(
        String codigoPredio,
        String numeroCatastral,
        String ubicacion,
        String usoPredio,
        TipoPredio tipoPredio,
        BigDecimal areaTerreno,
        LocalDate fechaAdquisicion,
        String condicionPropiedad,
        BigDecimal porcentajePropiedad) {

    public static PredioContribuyenteRespuesta desde(Predio predio, String codigoContribuyente) {
        Titularidad titularidad = predio.getTitulares().stream()
                .filter(t -> t.codigoContribuyente().equals(codigoContribuyente))
                .findFirst()
                .orElseThrow();
        return new PredioContribuyenteRespuesta(
                predio.getCodigoPredio(),
                predio.getNumeroCatastral(),
                predio.getUbicacion(),
                predio.getUsoPredio(),
                predio.getTipoPredio(),
                predio.getAreaTerreno(),
                predio.getFechaAdquisicion(),
                titularidad.condicionPropiedad(),
                titularidad.porcentajePropiedad());
    }
}
