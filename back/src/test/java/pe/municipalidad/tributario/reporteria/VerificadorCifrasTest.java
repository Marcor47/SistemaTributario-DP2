package pe.municipalidad.tributario.reporteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VerificadorCifrasTest {

    @Test
    void leeLosFormatosDeNumerosEnEspanolEIngles() {
        assertThat(VerificadorCifras.numeros("128,340.13 128.340,13 22,14 8.29 -8,3 1,000"))
                .containsExactly(128340.13, 128340.13, 22.14, 8.29, 8.3, 1000.0);
    }

    @Test
    void aceptaCifrasDelResultadoYRedondeos() {
        List<List<Object>> filas = List.of(List.<Object>of("Z01", 22.1389, 128340.13));
        assertThat(VerificadorCifras.inventadas("Z01 tiene 22,1 % y S/ 128,340.13, unos 128 mil soles.", filas, List.of("2026")))
                .isEmpty();
    }

    @Test
    void marcaLasCifrasQueNoEstanEnLosDatos() {
        List<List<Object>> filas = List.of(List.<Object>of(450.0));
        assertThat(VerificadorCifras.inventadas("La deuda es S/ 999.00 en 2024.", filas, List.of("2026")))
                .containsExactly(999.0, 2024.0);
    }
}
