package pe.municipalidad.tributario.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/* El algoritmo de HU030 sin base de datos: grupos separados, determinismo, silueta y escalado. */
class KMeansTest {

    /* Tres nubes de 20 puntos alrededor de (0,0), (10,0) y (0,10). */
    private static double[][] tresNubes() {
        SplittableRandom azar = new SplittableRandom(7);
        double[][] centros = {{0, 0}, {10, 0}, {0, 10}};
        double[][] x = new double[60][];
        for (int i = 0; i < 60; i++) {
            double[] c = centros[i / 20];
            x[i] = new double[] {c[0] + azar.nextDouble(-1, 1), c[1] + azar.nextDouble(-1, 1)};
        }
        return x;
    }

    @Test
    void encuentraGruposSeparados() {
        double[][] x = tresNubes();
        KMeans.Resultado r = KMeans.ajustar(x, 3, 10, 1);

        assertThat(r.tamanos()).containsExactlyInAnyOrder(20, 20, 20);
        for (int g = 0; g < 3; g++) {
            int etiqueta = r.etiquetas()[g * 20];
            assertThat(Arrays.copyOfRange(r.etiquetas(), g * 20, g * 20 + 20)).containsOnly(etiqueta);
        }
        assertThat(KMeans.silueta(x, r.etiquetas(), 3, 5000, 1)).isGreaterThan(0.8);
    }

    @Test
    void laSiluetaPrefiereElNumeroRealDeGrupos() {
        double[][] x = tresNubes();
        double conTres = KMeans.silueta(x, KMeans.ajustar(x, 3, 10, 1).etiquetas(), 3, 5000, 1);
        for (int k : new int[] {2, 4, 5}) {
            double otra = KMeans.silueta(x, KMeans.ajustar(x, k, 10, 1).etiquetas(), k, 5000, 1);
            assertThat(otra).as("k = %d", k).isLessThan(conTres);
        }
    }

    @Test
    void conLaMismaSemillaDaLoMismo() {
        double[][] x = tresNubes();
        KMeans.Resultado a = KMeans.ajustar(x, 4, 5, 42);
        KMeans.Resultado b = KMeans.ajustar(x, 4, 5, 42);

        assertThat(a.etiquetas()).isEqualTo(b.etiquetas());
        assertThat(a.inercia()).isEqualTo(b.inercia());
    }

    @Test
    void laSiluetaConMuestraSeAcercaALaCompleta() {
        double[][] x = tresNubes();
        int[] etiquetas = KMeans.ajustar(x, 3, 10, 1).etiquetas();

        assertThat(KMeans.silueta(x, etiquetas, 3, 30, 1))
                .isCloseTo(KMeans.silueta(x, etiquetas, 3, 5000, 1), org.assertj.core.data.Offset.offset(0.05));
        assertThat(KMeans.muestra(60, 30, 1)).hasSize(30).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void puntosRepetidosNoRompenElAjuste() {
        double[][] x = new double[10][];
        for (int i = 0; i < 10; i++) {
            x[i] = new double[] {i < 5 ? 0 : 1, 0};
        }
        KMeans.Resultado r = KMeans.ajustar(x, 3, 5, 1);

        assertThat(Arrays.stream(r.tamanos()).min().getAsInt()).isPositive();
        assertThat(r.inercia()).isZero();
    }

    @Test
    void escaladoLogaritmicoYEstandarizado() {
        // Pagar adelantado (atraso negativo) conserva el signo; deuda, antigüedad y meses van con ln(1+x).
        assertThat(SegmentacionModeloService.transformar(new double[] {90, -147, 0, 0, Math.E - 1}))
                .containsExactly(new double[] {90, -Math.log1p(147), 0, 0, 1}, org.assertj.core.data.Offset.offset(1e-9));

        double[] media = new double[2];
        double[] desviacion = new double[2];
        double[][] z = SegmentacionModeloService.estandarizar(new double[][] {{1, 5}, {3, 5}}, media, desviacion);
        assertThat(media).containsExactly(2, 5);
        assertThat(desviacion).containsExactly(1, 1);
        assertThat(z[0]).containsExactly(-1, 0);
    }

    @Test
    void lasComponentesPrincipalesSiguenLaDireccionDeMayorVarianza() {
        // Puntos sobre la recta y = x, con un poco de ruido en la otra dirección.
        double[][] x = new double[40][];
        for (int i = 0; i < 40; i++) {
            double t = i - 19.5;
            double ruido = (i % 2 == 0 ? 0.1 : -0.1);
            x[i] = new double[] {t + ruido, t - ruido};
        }
        SegmentacionModeloService.Pca pca = SegmentacionModeloService.componentesPrincipales(x, 2);

        double[] primera = pca.componentes()[0];
        assertThat(Math.abs(primera[0])).isCloseTo(Math.sqrt(0.5), org.assertj.core.data.Offset.offset(1e-3));
        assertThat(primera[0] * primera[1]).isPositive();
        assertThat(pca.varianza()[0]).isGreaterThan(0.99);
        double producto = primera[0] * pca.componentes()[1][0] + primera[1] * pca.componentes()[1][1];
        assertThat(producto).isCloseTo(0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
