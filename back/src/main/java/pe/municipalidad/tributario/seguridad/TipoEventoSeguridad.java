package pe.municipalidad.tributario.seguridad;

/* Eventos de seguridad registrados (RNF-024). */
public enum TipoEventoSeguridad {
    LOGIN_OK,
    LOGIN_FALLIDO,
    LOGOUT,
    CAMBIO_CLAVE,
    CREACION_USUARIO,
    CAMBIO_ROL,
    ACCESO_DENEGADO,
    ACCESO_DATO_PERSONAL
}
