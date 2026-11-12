package pe.municipalidad.tributario.ia;

import java.util.Arrays;
import java.util.SplittableRandom;

/*
 * K-means (inicio k-means++ y algoritmo de Lloyd) y coeficiente de silueta, para la segmentación de RF-043.
 * Determinista: con la misma semilla y los mismos datos da siempre el mismo resultado. Se queda con el mejor de
 * varios reinicios (menor inercia), como scikit-learn con n_init. Sin dependencias: los datos son una matriz de
 * n contribuyentes × d variables ya escaladas.
 */
final class KMeans {

    private static final int MAX_ITERACIONES = 300;

    private KMeans() {
    }

    record Resultado(int k, int[] etiquetas, double[][] centros, double inercia) {

        int[] tamanos() {
            int[] t = new int[k];
            for (int e : etiquetas) {
                t[e]++;
            }
            return t;
        }

        int tamanoMinimo() {
            return Arrays.stream(tamanos()).min().orElse(0);
        }
    }

    static Resultado ajustar(double[][] x, int k, int reinicios, long semilla) {
        if (k < 1 || k > x.length) {
            throw new IllegalArgumentException("k debe estar entre 1 y " + x.length);
        }
        SplittableRandom azar = new SplittableRandom(semilla);
        Resultado mejor = null;
        for (int r = 0; r < reinicios; r++) {
            Resultado intento = lloyd(x, iniciar(x, k, azar.split()));
            if (mejor == null || intento.inercia() < mejor.inercia() - 1e-12) {
                mejor = intento;
            }
        }
        return mejor;
    }

    /* k-means++: cada centro nuevo se elige con probabilidad proporcional a la distancia² al centro más cercano. */
    private static double[][] iniciar(double[][] x, int k, SplittableRandom azar) {
        int n = x.length;
        double[][] centros = new double[k][];
        centros[0] = x[azar.nextInt(n)].clone();
        double[] d2 = new double[n];
        Arrays.fill(d2, Double.MAX_VALUE);
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                d2[i] = Math.min(d2[i], distancia2(x[i], centros[c - 1]));
                total += d2[i];
            }
            int elegido = n - 1;
            if (total > 0) {
                double umbral = azar.nextDouble() * total;
                double acumulado = 0;
                for (int i = 0; i < n; i++) {
                    acumulado += d2[i];
                    if (acumulado >= umbral) {
                        elegido = i;
                        break;
                    }
                }
            } else {
                elegido = azar.nextInt(n);
            }
            centros[c] = x[elegido].clone();
        }
        return centros;
    }

    private static Resultado lloyd(double[][] x, double[][] centros) {
        int n = x.length;
        int k = centros.length;
        int d = x[0].length;
        int[] etiquetas = new int[n];
        Arrays.fill(etiquetas, -1);
        for (int iteracion = 0; iteracion < MAX_ITERACIONES; iteracion++) {
            boolean cambio = false;
            for (int i = 0; i < n; i++) {
                int cercano = cercano(x[i], centros);
                if (cercano != etiquetas[i]) {
                    etiquetas[i] = cercano;
                    cambio = true;
                }
            }
            if (!cambio) {
                break;
            }
            double[][] suma = new double[k][d];
            int[] cuenta = new int[k];
            for (int i = 0; i < n; i++) {
                cuenta[etiquetas[i]]++;
                for (int j = 0; j < d; j++) {
                    suma[etiquetas[i]][j] += x[i][j];
                }
            }
            for (int c = 0; c < k; c++) {
                if (cuenta[c] == 0) {
                    // Segmento vacío: se lleva el punto más alejado de su centro.
                    int lejano = masLejano(x, centros, etiquetas);
                    cuenta[etiquetas[lejano]]--;
                    for (int j = 0; j < d; j++) {
                        suma[etiquetas[lejano]][j] -= x[lejano][j];
                    }
                    etiquetas[lejano] = c;
                    cuenta[c] = 1;
                    suma[c] = x[lejano].clone();
                }
            }
            for (int c = 0; c < k; c++) {
                for (int j = 0; j < d; j++) {
                    centros[c][j] = suma[c][j] / cuenta[c];
                }
            }
        }
        double inercia = 0;
        for (int i = 0; i < n; i++) {
            inercia += distancia2(x[i], centros[etiquetas[i]]);
        }
        return new Resultado(k, etiquetas, centros, inercia);
    }

    /*
     * Silueta media: para cada punto, (b - a) / max(a, b), con a la distancia media a su propio segmento y b la del
     * segmento vecino más cercano. Es O(n²): con más de `muestra` puntos se calcula sobre una muestra fija.
     */
    static double silueta(double[][] x, int[] etiquetas, int k, int muestra, long semilla) {
        int[] idx = muestra(x.length, muestra, semilla);
        double[] valores = siluetas(x, etiquetas, k, idx);
        return Arrays.stream(valores).average().orElse(0);
    }

    /* Silueta de cada punto de idx, comparando solo contra los puntos de idx. */
    static double[] siluetas(double[][] x, int[] etiquetas, int k, int[] idx) {
        int m = idx.length;
        int[] tamano = new int[k];
        for (int i : idx) {
            tamano[etiquetas[i]]++;
        }
        double[] resultado = new double[m];
        for (int a = 0; a < m; a++) {
            int i = idx[a];
            double[] suma = new double[k];
            for (int j : idx) {
                if (j != i) {
                    suma[etiquetas[j]] += Math.sqrt(distancia2(x[i], x[j]));
                }
            }
            int propio = etiquetas[i];
            if (tamano[propio] <= 1) {
                resultado[a] = 0;
                continue;
            }
            double dentro = suma[propio] / (tamano[propio] - 1);
            double vecino = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c != propio && tamano[c] > 0) {
                    vecino = Math.min(vecino, suma[c] / tamano[c]);
                }
            }
            double mayor = Math.max(dentro, vecino);
            resultado[a] = mayor == 0 ? 0 : (vecino - dentro) / mayor;
        }
        return resultado;
    }

    static int[] muestra(int n, int muestra, long semilla) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        if (n <= muestra) {
            return idx;
        }
        SplittableRandom azar = new SplittableRandom(semilla);
        for (int i = n - 1; i > 0; i--) {
            int j = azar.nextInt(i + 1);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        int[] parte = Arrays.copyOf(idx, muestra);
        Arrays.sort(parte);
        return parte;
    }

    static int cercano(double[] punto, double[][] centros) {
        int mejor = 0;
        double minimo = Double.MAX_VALUE;
        for (int c = 0; c < centros.length; c++) {
            double d = distancia2(punto, centros[c]);
            if (d < minimo) {
                minimo = d;
                mejor = c;
            }
        }
        return mejor;
    }

    private static int masLejano(double[][] x, double[][] centros, int[] etiquetas) {
        int lejano = 0;
        double maximo = -1;
        for (int i = 0; i < x.length; i++) {
            double d = distancia2(x[i], centros[etiquetas[i]]);
            if (d > maximo) {
                maximo = d;
                lejano = i;
            }
        }
        return lejano;
    }

    static double distancia2(double[] a, double[] b) {
        double s = 0;
        for (int j = 0; j < a.length; j++) {
            double dif = a[j] - b[j];
            s += dif * dif;
        }
        return s;
    }
}
