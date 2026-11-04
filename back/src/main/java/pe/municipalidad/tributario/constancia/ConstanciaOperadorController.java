package pe.municipalidad.tributario.constancia;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU020: bandeja del operador de Rentas para las solicitudes de Constancia de No Adeudo. Requiere el permiso
 * CONSTANCIAS:GESTIONAR; el operador se toma del usuario interno autenticado.
 */
@RestController
@RequestMapping("/api/gestion/constancias")
public class ConstanciaOperadorController {

    private final ConstanciaOperadorService constanciaOperadorService;

    public ConstanciaOperadorController(ConstanciaOperadorService constanciaOperadorService) {
        this.constanciaOperadorService = constanciaOperadorService;
    }

    @GetMapping
    public List<SolicitudGestionRespuesta> listar(@RequestParam(required = false) EstadoConstancia estado) {
        return constanciaOperadorService.listar(estado);
    }

    @GetMapping("/{idSolicitud}")
    public SolicitudGestionRespuesta obtener(@PathVariable Long idSolicitud) {
        return constanciaOperadorService.obtener(idSolicitud);
    }

    @PostMapping("/{idSolicitud}/verificacion")
    public SolicitudGestionRespuesta verificarDeuda(@PathVariable Long idSolicitud, Principal operador) {
        return constanciaOperadorService.verificarDeuda(idSolicitud, operador.getName());
    }

    @PostMapping("/{idSolicitud}/emision")
    public SolicitudGestionRespuesta emitir(@PathVariable Long idSolicitud, Principal operador) {
        return constanciaOperadorService.emitir(idSolicitud, operador.getName());
    }

    @PostMapping("/{idSolicitud}/rechazo")
    public SolicitudGestionRespuesta rechazar(@PathVariable Long idSolicitud,
            @Valid @RequestBody RechazoSolicitudConstancia rechazo, Principal operador) {
        return constanciaOperadorService.rechazar(idSolicitud, rechazo.motivo(), operador.getName());
    }
}
