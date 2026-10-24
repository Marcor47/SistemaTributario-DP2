package pe.municipalidad.tributario.integracion.autenticacion;

import java.util.Optional;

/*
 * Servicio de autenticación de la Municipalidad para el contribuyente y validación de identidad por DNI
 * (RF-054, RNF-028). Se elige con tributario.integracion.autenticacion: api (por defecto, ps_login sobre
 * la BD sintética) o simulado.
 */
public interface AutenticacionMunicipalGateway {

    /* Vacío si las credenciales no son válidas; nunca devuelve la clave ni un token municipal. */
    Optional<ContribuyenteAutenticado> autenticar(String documento, String clave);

    /* Nombre registrado para el DNI, si existe. */
    Optional<String> validarIdentidad(String dni);

    record ContribuyenteAutenticado(String codigoContribuyente, String nombre) {
    }
}
