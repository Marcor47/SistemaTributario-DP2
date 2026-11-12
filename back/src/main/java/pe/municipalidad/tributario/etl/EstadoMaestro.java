package pe.municipalidad.tributario.etl;

public enum EstadoMaestro {
    ACTIVA,
    PENDIENTE_REVISION,
    /* Entidad provisional que se unió a otra al resolver su caso; ya no tiene correspondencias. */
    FUSIONADA
}
