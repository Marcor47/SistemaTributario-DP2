package pe.municipalidad.tributario.reporteria;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.reporte.DimensionRestringida;
import pe.municipalidad.tributario.reporte.RestriccionReporteRepository;
import pe.municipalidad.tributario.reporte.TipoReporte;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/** Reglas por petición; nunca se guardan en la carga analítica compartida. Las denegaciones prevalecen. */
@Service
public class AccesoReporteria {
    public record Regla(TipoReporte reporte, DimensionRestringida dimension, String valor) {
        boolean restringe(String zona, String segmento) {
            return switch (dimension) {
                case REPORTE -> true;
                case ZONA -> zona == null || CuboReporteria.SIN_ZONA.equals(zona) || valor.equalsIgnoreCase(zona);
                case SEGMENTO -> segmento == null || CuboReporteria.SIN_SEGMENTO.equals(segmento) || valor.equalsIgnoreCase(segmento);
            };
        }
    }

    private final UsuarioRepository usuarios;
    private final RestriccionReporteRepository restricciones;
    public AccesoReporteria(UsuarioRepository usuarios, RestriccionReporteRepository restricciones) {
        this.usuarios = usuarios; this.restricciones = restricciones;
    }

    @Transactional(readOnly = true)
    public List<Regla> actuales() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes contexto)) return List.of();
        var request = contexto.getRequest();
        var sesion = request.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO);
        Usuario usuario;
        if (id instanceof Long idUsuario) {
            usuario = usuarios.findById(idUsuario).orElseThrow(AccesoReporteria::sinSesion);
        } else if (request.getUserPrincipal() != null) {
            usuario = usuarios.findByNombreUsuario(request.getUserPrincipal().getName()).orElseThrow(AccesoReporteria::sinSesion);
        } else {
            throw sinSesion();
        }
        if (!usuario.isActivo()) throw sinSesion();
        List<Regla> reglas = new ArrayList<>();
        for (var rol : usuario.getRoles()) {
            for (var tipo : TipoReporte.values()) {
                restricciones.findByRolAndTipoReporte(rol, tipo).stream().filter(r -> !r.isPermitido())
                        .map(r -> new Regla(tipo, r.getDimension(), r.getValorPermitido())).forEach(reglas::add);
            }
        }
        return List.copyOf(reglas);
    }

    private static NoAutenticadoException sinSesion() {
        return new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno.");
    }
}
