package pe.municipalidad.tributario.predios;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU036 (RF-048): registro de un predio nuevo con su contribuyente en SAMNET. Requiere PREDIOS:REGISTRAR.
 * 201 con el predio y su titular; 409 PREDIO_YA_REGISTRADO si el código ya existe; 404 si el contribuyente no existe.
 */
@RestController
@RequestMapping("/api/predios")
public class PredioRegistroController {

    private final RegistroPredioService servicio;

    public PredioRegistroController(RegistroPredioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<PredioRespuesta> registrar(@Valid @RequestBody RegistrarPredioSolicitud solicitud) {
        PredioRespuesta predio = servicio.registrar(solicitud);
        return ResponseEntity.created(URI.create("/api/predios?consulta=" + predio.codigoPredio())).body(predio);
    }
}
