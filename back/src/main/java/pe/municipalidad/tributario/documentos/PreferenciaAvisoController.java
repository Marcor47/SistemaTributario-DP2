package pe.municipalidad.tributario.documentos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.auth.SesionContribuyente;

/* HU016 (RF-022): preferencias de aviso del contribuyente (P12), siempre tomadas de su sesión. */
@RestController
@RequestMapping("/api/portal/preferencias-avisos")
public class PreferenciaAvisoController {

    public record Peticion(@NotNull(message = "es obligatorio") Boolean recordatorioVencimiento) {
    }

    private final PreferenciaAvisoService servicio;

    public PreferenciaAvisoController(PreferenciaAvisoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PreferenciaAvisoService.PreferenciaAvisoRespuesta obtener(HttpServletRequest request) {
        return servicio.obtener(SesionContribuyente.codigoContribuyente(request));
    }

    @PutMapping
    public PreferenciaAvisoService.PreferenciaAvisoRespuesta actualizar(@Valid @RequestBody Peticion peticion,
            HttpServletRequest request) {
        return servicio.actualizar(SesionContribuyente.codigoContribuyente(request),
                peticion.recordatorioVencimiento());
    }
}
