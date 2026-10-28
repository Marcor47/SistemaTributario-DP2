package pe.municipalidad.tributario.predios;

import java.util.List;
import pe.municipalidad.tributario.common.DemoInternalAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/predios")
@Profile("demo")
public class PredioController {
    private final PredioGateway gateway;
    private final DemoInternalAccess access;

    public PredioController(PredioGateway gateway, DemoInternalAccess access) {
        this.gateway = gateway;
        this.access = access;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<Predio> buscar(@RequestParam String consulta,
            @RequestHeader(value = "X-Demo-Internal-Key", required = false) String key) {
        access.requireRead(key);
        if (consulta == null || consulta.isBlank() || consulta.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta inválida");
        }
        return gateway.buscar(consulta.trim());
    }
}
