package pe.municipalidad.tributario.documentos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* HU016 (RF-022): recordatorios de vencimiento por correo, su anticipación y su estado por obligación. */
@RestController
@RequestMapping("/api/recordatorios-vencimiento")
public class RecordatorioVencimientoController {

    public record ConfiguracionPeticion(
            @NotNull(message = "es obligatorio") @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 10, message = "no puede ser mayor que 10") Integer diasAnticipacion) {
    }

    private final RecordatorioVencimientoService servicio;

    public RecordatorioVencimientoController(RecordatorioVencimientoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/procesar")
    public RecordatorioVencimientoService.Resultado procesar() {
        return servicio.procesar();
    }

    @GetMapping("/configuracion")
    public RecordatorioVencimientoService.Configuracion configuracion() {
        return servicio.configuracion();
    }

    @PutMapping("/configuracion")
    public RecordatorioVencimientoService.Configuracion guardar(@Valid @RequestBody ConfiguracionPeticion peticion,
            Principal usuario) {
        return servicio.guardarConfiguracion(peticion.diasAnticipacion(), usuario.getName());
    }

    @GetMapping("/obligaciones/{idObligacion}")
    public RecordatorioVencimientoService.EstadoRecordatorio obligacion(@PathVariable long idObligacion) {
        return servicio.estadoDe(idObligacion);
    }
}
