package pe.municipalidad.tributario.integracion.correo;

/* Envío de avisos por correo electrónico, único canal de aviso (RF-013, RF-022). */
public interface CorreoGateway {

    void enviarCorreo(String destino, String asunto, String cuerpo);

    /*
     * HU016: variante con cuerpo HTML. Las implementaciones que no lo soportan (por ejemplo el registro
     * local o las alertas de mora) siguen usando el texto plano.
     */
    default void enviarCorreo(String destino, String asunto, String cuerpo, String html) {
        enviarCorreo(destino, asunto, cuerpo);
    }

    default boolean registraSinEnviar() { return false; }
}
