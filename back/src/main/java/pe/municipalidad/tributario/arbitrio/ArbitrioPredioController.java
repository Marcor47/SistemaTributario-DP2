package pe.municipalidad.tributario.arbitrio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/* HU005: el Analista de Rentas consulta los arbitrios de un predio. Contrato en back/HU005_API.md. */
@RestController
@RequestMapping("/api/predios")
public class ArbitrioPredioController {

    private final ArbitrioPredioService servicio;
    private final SesionInterna sesion;

    public ArbitrioPredioController(ArbitrioPredioService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping("/{codigoPredio}/arbitrios")
    public ArbitrioPredioRespuesta arbitrios(
            @PathVariable @NotBlank(message = "es obligatorio") @Size(max = 20, message = "es demasiado largo")
            String codigoPredio, HttpServletRequest request) {
        sesion.conRol(request, NombreRol.ANALISTA_RENTAS, NombreRol.ADMINISTRADOR);
        return servicio.detalle(codigoPredio.trim());
    }
}
