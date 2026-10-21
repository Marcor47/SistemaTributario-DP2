package pe.municipalidad.tributario.indicador;

import java.time.LocalDate;
import java.time.YearMonth;

/*
 * Define cómo se numeran los periodos de una meta dentro de un año:
 * MENSUAL usa 1 a 12, TRIMESTRAL 1 a 4 y ANUAL solo 1.
 */
public enum Periodicidad {

    MENSUAL(12, 1),
    TRIMESTRAL(4, 3),
    ANUAL(1, 12);

    private final int periodosPorAnio;
    private final int mesesPorPeriodo;

    Periodicidad(int periodosPorAnio, int mesesPorPeriodo) {
        this.periodosPorAnio = periodosPorAnio;
        this.mesesPorPeriodo = mesesPorPeriodo;
    }

    public int getPeriodosPorAnio() {
        return periodosPorAnio;
    }

    public boolean esPeriodoValido(int numeroPeriodo) {
        return numeroPeriodo >= 1 && numeroPeriodo <= periodosPorAnio;
    }

    public LocalDate fechaFin(int anio, int numeroPeriodo) {
        return YearMonth.of(anio, numeroPeriodo * mesesPorPeriodo).atEndOfMonth();
    }

    /* Etiqueta legible para el front: 2026-03, 2026-T2 o 2026. */
    public String etiqueta(int anio, int numeroPeriodo) {
        return switch (this) {
            case MENSUAL -> String.format("%d-%02d", anio, numeroPeriodo);
            case TRIMESTRAL -> anio + "-T" + numeroPeriodo;
            case ANUAL -> String.valueOf(anio);
        };
    }
}
