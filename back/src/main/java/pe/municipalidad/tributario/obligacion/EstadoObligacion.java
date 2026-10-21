package pe.municipalidad.tributario.obligacion;

/* No se guarda en la BD: se deriva del saldo y de la fecha de vencimiento. */
public enum EstadoObligacion {
    PAGADA,
    PENDIENTE,
    VENCIDA
}
