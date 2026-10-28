package pe.municipalidad.tributario.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import pe.municipalidad.tributario.common.NoAutenticadoException;

/*
 * Identidad del contribuyente en la sesión propia del sistema (RNF-028). Los trámites la toman de aquí y
 * nunca de parámetros de la petición, para que nadie opere a nombre de otro (RNF-023).
 */
public final class SesionContribuyente {

    public static final String ATRIBUTO_CODIGO_CONTRIBUYENTE = "citizenCode";

    private SesionContribuyente() {
    }

    public static String codigoContribuyente(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object codigo = sesion == null ? null : sesion.getAttribute(ATRIBUTO_CODIGO_CONTRIBUYENTE);
        if (!(codigo instanceof String texto) || texto.isBlank()) {
            throw new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión para continuar con el trámite.");
        }
        return texto;
    }
}
