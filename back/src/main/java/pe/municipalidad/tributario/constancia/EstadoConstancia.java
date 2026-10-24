package pe.municipalidad.tributario.constancia;

/*
 * Avance del trámite de la constancia, en el orden que controla el servidor (RNF-029). Un operador verifica
 * la deuda, emite la orden de pago y, tras el pago, emite la constancia (RF-034).
 */
public enum EstadoConstancia {
    INICIADA,
    FIRMADA,
    DEUDA_VERIFICADA,
    ORDEN_PAGO_EMITIDA,
    PAGADA,
    EMITIDA,
    RECHAZADA
}
