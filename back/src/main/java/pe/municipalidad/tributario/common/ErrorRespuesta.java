package pe.municipalidad.tributario.common;

import java.util.List;

/*
 * Formato único de error de toda la API. El front decide qué mostrar a partir
 * de "codigo"; "mensaje" ya está redactado para el usuario final.
 */
public record ErrorRespuesta(String codigo, String mensaje, List<String> detalles) {

    public static ErrorRespuesta de(String codigo, String mensaje) {
        return new ErrorRespuesta(codigo, mensaje, List.of());
    }
}
