package pe.municipalidad.tributario.reporteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Comprueba que cada cifra de la respuesta del modelo salga del resultado de la consulta, de la pregunta, de los filtros
 * o de la fecha de corte. Los modelos pequeños inventan cifras: una respuesta que no pasa no se muestra al usuario.
 */
final class VerificadorCifras {

    private static final Pattern NUMERO = Pattern.compile("-?\\d[\\d.,]*\\d|-?\\d");

    private VerificadorCifras() {
    }

    /* Admite 128,340.13, 128.340,13, 22,14, 8.29 y -8,3. Devuelve valores absolutos. */
    static List<Double> numeros(String texto) {
        List<Double> valores = new ArrayList<>();
        if (texto == null) {
            return valores;
        }
        Matcher coincidencia = NUMERO.matcher(texto);
        while (coincidencia.find()) {
            String t = coincidencia.group();
            int coma = t.lastIndexOf(',');
            int punto = t.lastIndexOf('.');
            if (coma >= 0 && punto >= 0) {
                t = punto > coma ? t.replace(",", "") : t.replace(".", "").replace(',', '.');
            } else if (coma >= 0 && t.indexOf(',') == coma && t.length() - coma - 1 != 3) {
                t = t.replace(',', '.');
            } else {
                t = t.replace(",", "");
                if (t.indexOf('.') != t.lastIndexOf('.')) {
                    t = t.replace(".", "");
                }
            }
            try {
                valores.add(Math.abs(Double.parseDouble(t)));
            } catch (NumberFormatException ex) {
                continue;
            }
        }
        return valores;
    }

    /* Cifras de la respuesta que no se pueden rastrear hasta las filas ni hasta los textos indicados. */
    static List<Double> inventadas(String respuesta, List<List<Object>> filas, List<String> textos) {
        List<Double> base = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            base.add((double) i);
        }
        for (List<Object> fila : filas) {
            for (Object valor : fila) {
                if (valor instanceof Number n) {
                    base.add(Math.abs(n.doubleValue()));
                    base.add(Math.abs(n.doubleValue()) * 100);
                } else if (valor != null) {
                    base.addAll(numeros(String.valueOf(valor)));
                }
            }
        }
        textos.forEach(texto -> base.addAll(numeros(texto)));
        Set<Double> resultado = new TreeSet<>();
        for (double numero : numeros(respuesta)) {
            if (!explicada(numero, base)) {
                resultado.add(numero);
            }
        }
        return new ArrayList<>(resultado);
    }

    /* ±0,05 para tasas y valores pequeños; 0,05 % para importes (2024 no debe coincidir con 2026). */
    private static boolean explicada(double numero, List<Double> base) {
        for (double b : base) {
            double tolerancia = b < 1000 ? 0.051 : b * 0.0005;
            if (Math.abs(numero - b) <= tolerancia || Math.abs(numero * 1000 - b) <= b * 0.005
                    || Math.abs(numero * 1_000_000 - b) <= b * 0.005) {
                return true;
            }
        }
        return false;
    }
}
