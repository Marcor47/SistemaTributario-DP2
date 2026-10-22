package pe.municipalidad.tributario.indicador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodicidadTest {

    @Test
    void mensualTerminaElUltimoDiaDelMesIncluyendoBisiestos() {
        assertThat(Periodicidad.MENSUAL.fechaFin(2028, 2)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(Periodicidad.MENSUAL.fechaFin(2026, 2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(Periodicidad.MENSUAL.etiqueta(2026, 3)).isEqualTo("2026-03");
    }

    @Test
    void trimestralTerminaAlCierreDelTercerMes() {
        assertThat(Periodicidad.TRIMESTRAL.fechaFin(2026, 1)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(Periodicidad.TRIMESTRAL.fechaFin(2026, 4)).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(Periodicidad.TRIMESTRAL.etiqueta(2026, 2)).isEqualTo("2026-T2");
    }

    @Test
    void anualSoloAdmiteElPeriodoUno() {
        assertThat(Periodicidad.ANUAL.esPeriodoValido(1)).isTrue();
        assertThat(Periodicidad.ANUAL.esPeriodoValido(2)).isFalse();
        assertThat(Periodicidad.ANUAL.fechaFin(2026, 1)).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(Periodicidad.ANUAL.etiqueta(2026, 1)).isEqualTo("2026");
    }

    @Test
    void rechazaPeriodosFueraDeRango() {
        assertThat(Periodicidad.MENSUAL.esPeriodoValido(0)).isFalse();
        assertThat(Periodicidad.MENSUAL.esPeriodoValido(13)).isFalse();
        assertThat(Periodicidad.TRIMESTRAL.esPeriodoValido(5)).isFalse();
    }
}
