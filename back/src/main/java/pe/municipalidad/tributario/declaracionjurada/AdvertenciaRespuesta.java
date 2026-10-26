package pe.municipalidad.tributario.declaracionjurada;

public record AdvertenciaRespuesta(String codigo, String mensaje) {

    public static AdvertenciaRespuesta de(String codigo, String mensaje) {
        return new AdvertenciaRespuesta(codigo, mensaje);
    }
}
