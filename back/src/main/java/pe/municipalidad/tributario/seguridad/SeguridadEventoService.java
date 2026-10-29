package pe.municipalidad.tributario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SeguridadEventoService {
    private final EventoSeguridadRepository eventos;
    private final Clock reloj;

    public SeguridadEventoService(EventoSeguridadRepository eventos, Clock reloj) {
        this.eventos = eventos;
        this.reloj = reloj;
    }

    public void accesoDenegado(HttpServletRequest request, String usuario) {
        String detalle = request.getMethod() + " " + request.getRequestURI();
        eventos.save(new EventoSeguridad(TipoEventoSeguridad.ACCESO_DENEGADO,
                usuario == null || usuario.isBlank() ? "anonimo" : usuario,
                request.getRemoteAddr(), detalle, LocalDateTime.now(reloj)));
    }
}
