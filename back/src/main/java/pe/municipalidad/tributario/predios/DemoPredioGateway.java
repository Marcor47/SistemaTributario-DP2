package pe.municipalidad.tributario.predios;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoPredioGateway implements PredioGateway {
    private static final List<Predio> PREDIOS = List.of(
            new Predio("DEM-001", "CAT-DEM-001", "Av. Ejemplo 100", "Activo", "Vivienda",
                    "Casa", "LP", "", new BigDecimal("120"), LocalDate.of(2020, 1, 1),
                    new BigDecimal("50000"), new BigDecimal("30000"), BigDecimal.ZERO,
                    new BigDecimal("80000"), "",
                    List.of(new Predio.Contribuyente("DEMO-01", "CONTRIBUYENTE DE PRUEBA", "DNI",
                            "00000001", new BigDecimal("100")))),
            new Predio("DEM-002", "CAT-DEM-002", "Jr. Muestra 200", "Activo", "Comercio",
                    "Local", "PJ", "Comercio", new BigDecimal("85"), LocalDate.of(2021, 2, 1),
                    new BigDecimal("40000"), new BigDecimal("20000"), BigDecimal.ZERO,
                    new BigDecimal("60000"), "",
                    List.of(new Predio.Contribuyente("DEMO-02", "EMPRESA DE PRUEBA", "RUC",
                                    "20000000001", new BigDecimal("50")),
                            new Predio.Contribuyente("DEMO-03", "SOCIO DE PRUEBA", "DNI",
                                    "00000003", new BigDecimal("50")))));

    @Override
    public List<Predio> buscar(String consulta) {
        String q = normalizar(consulta);
        return PREDIOS.stream().filter(p -> normalizar(p.codPredio()).contains(q)
                || p.contribuyentes().stream().anyMatch(t -> normalizar(t.codigo()).contains(q)
                        || normalizar(t.nombre()).contains(q) || normalizar(t.doc()).contains(q))).toList();
    }

    private static String normalizar(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
