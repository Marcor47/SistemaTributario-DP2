package pe.municipalidad.tributario.reporteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pe.municipalidad.tributario.reporteria.CatalogoReporteriaRespuesta.Opcion;
import tools.jackson.databind.JsonNode;

/*
 * Consulta que propone el modelo. Solo se ejecuta si cada valor pertenece al catálogo: el modelo nunca escribe SQL ni
 * llega a la base de datos. La respuesta se calcula con los mismos agregados que muestran las pantallas.
 */
record ConsultaAsistente(List<Indicador> indicadores, Agrupacion agrupacion, List<Integer> anios, String zona,
        String tributo, String segmento, String orden, Integer limite) {

    static final int LIMITE_MAXIMO = 50;

    enum Agrupacion {
        NINGUNO, ANIO, MES, ZONA, TRIBUTO, SEGMENTO, CONTRIBUYENTE
    }

    enum Indicador {
        EMITIDO("Emitido", true, false),
        COBRADO("Cobrado", true, false),
        RECAUDADO("Recaudado", true, false),
        DEUDA_VENCIDA("Deuda vencida", true, false),
        INDICE_MOROSIDAD("Índice de morosidad (%)", false, false),
        PORCENTAJE_COBRADO("Porcentaje cobrado (%)", false, false),
        REGULARIDAD_PAGO("Regularidad de pago (%)", false, false),
        CUOTAS("Cuotas", false, false),
        CONTRIBUYENTES("Contribuyentes", false, true),
        CONTRIBUYENTES_MOROSOS("Contribuyentes morosos", false, true),
        BUENOS_PAGADORES("Buenos pagadores", false, true);

        final String etiqueta;
        final boolean monto;
        final boolean deContribuyentes;

        Indicador(String etiqueta, boolean monto, boolean deContribuyentes) {
            this.etiqueta = etiqueta;
            this.monto = monto;
            this.deContribuyentes = deContribuyentes;
        }
    }

    static final class Invalida extends RuntimeException {
        Invalida(String mensaje) {
            super(mensaje);
        }
    }

    static ConsultaAsistente de(JsonNode nodo, CatalogoReporteriaRespuesta catalogo) {
        if (nodo == null || !nodo.isObject()) {
            throw new Invalida("La respuesta debe ser un objeto JSON.");
        }
        List<Indicador> indicadores = new ArrayList<>();
        for (JsonNode valor : nodo.path("indicadores")) {
            try {
                indicadores.add(Indicador.valueOf(valor.asString("").toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new Invalida("El indicador \"" + valor.asString("") + "\" no existe.");
            }
        }
        if (indicadores.isEmpty()) {
            throw new Invalida("Indica al menos un indicador.");
        }
        Agrupacion agrupacion;
        try {
            agrupacion = Agrupacion.valueOf(nodo.path("agrupar_por").asString("ninguno").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new Invalida("La agrupación \"" + nodo.path("agrupar_por").asString("") + "\" no existe.");
        }
        if (agrupacion == Agrupacion.MES && indicadores.stream().anyMatch(i -> i.deContribuyentes)) {
            throw new Invalida("Los indicadores de contribuyentes no se pueden agrupar por mes.");
        }
        List<Integer> anios = new ArrayList<>();
        for (JsonNode valor : nodo.path("anios")) {
            if (!valor.isIntegralNumber() || valor.asInt() < 2000 || valor.asInt() > 2100) {
                throw new Invalida("Los años deben ser números entre 2000 y 2100.");
            }
            anios.add(valor.asInt());
        }
        String orden = texto(nodo.path("orden"));
        if (orden != null && !orden.equalsIgnoreCase("asc") && !orden.equalsIgnoreCase("desc")) {
            throw new Invalida("El orden debe ser asc o desc.");
        }
        Integer limite = nodo.path("limite").isIntegralNumber()
                ? Math.max(1, Math.min(nodo.path("limite").asInt(), LIMITE_MAXIMO)) : null;
        return new ConsultaAsistente(indicadores, agrupacion, anios,
                codigo(texto(nodo.path("zona")), catalogo.zonas(), "zona"),
                codigo(texto(nodo.path("tributo")), catalogo.tributos(), "tributo"),
                segmento(texto(nodo.path("segmento")), catalogo.segmentos()),
                orden == null ? null : orden.toLowerCase(Locale.ROOT), limite);
    }

    private static String texto(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
            return null;
        }
        String valor = nodo.asString("").strip();
        return valor.isEmpty() ? null : valor;
    }

    private static String codigo(String valor, List<Opcion> opciones, String dimension) {
        if (valor == null) {
            return null;
        }
        return opciones.stream()
                .filter(o -> o.codigo().equalsIgnoreCase(valor) || o.nombre().equalsIgnoreCase(valor))
                .map(Opcion::codigo).findFirst()
                .orElseThrow(() -> new Invalida("La " + dimension + " \"" + valor + "\" no está en el catálogo."));
    }

    private static String segmento(String valor, List<String> segmentos) {
        if (valor == null) {
            return null;
        }
        return segmentos.stream().filter(valor::equalsIgnoreCase).findFirst()
                .orElseThrow(() -> new Invalida("El segmento \"" + valor + "\" no está en el catálogo."));
    }
}
