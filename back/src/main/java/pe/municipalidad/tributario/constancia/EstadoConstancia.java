package pe.municipalidad.tributario.constancia;

/* Avance del trámite de la constancia, en el orden que controla el servidor (RNF-029). */
public enum EstadoConstancia {
    INICIADA,
    DEUDA_VERIFICADA,
    FIRMADA,
    PAGADA,
    EMITIDA,
    RECHAZADA
}
