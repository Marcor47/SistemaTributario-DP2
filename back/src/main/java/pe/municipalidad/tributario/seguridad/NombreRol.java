package pe.municipalidad.tributario.seguridad;

/*
 * Perfiles de acceso de RF-047 más el Gestor de reclamos de RF-064. La base inicial implementa
 * administrador, ventanilla y gerencia.
 */
public enum NombreRol {
    ADMINISTRADOR,
    GERENCIA,
    ANALISTA_RENTAS,
    RESPONSABLE_DATOS,
    VENTANILLA,
    TERMINALISTA,
    GESTOR_RECLAMOS
}
