package pe.municipalidad.tributario.common;

import org.springframework.http.HttpStatus;

/*
 * Excepción base de las reglas de negocio. Cada subclase fija el estado HTTP
 * y el código de error que recibe el front.
 */
public abstract class NegocioException extends RuntimeException {

    private final String codigo;

    protected NegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public abstract HttpStatus getEstadoHttp();
}
