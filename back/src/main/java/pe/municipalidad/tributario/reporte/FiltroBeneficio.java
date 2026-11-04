package pe.municipalidad.tributario.reporte;

/*
 * Filtro del reporte de morosos según el beneficio aplicado a cada obligación.
 * CON_BENEFICIO incluye obligaciones con descuento; SIN_BENEFICIO, las que no lo tienen.
 */
public enum FiltroBeneficio {
    AMBOS,
    CON_BENEFICIO,
    SIN_BENEFICIO
}
