package pe.municipalidad.tributario.integracion.correo;

/* Envío de avisos por correo electrónico, único canal de aviso (RF-013, RF-022). */
public interface CorreoGateway {

    void enviarCorreo(String destino, String asunto, String cuerpo);
}
