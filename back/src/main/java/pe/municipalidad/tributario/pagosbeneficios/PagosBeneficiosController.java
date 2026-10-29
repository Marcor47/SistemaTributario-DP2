package pe.municipalidad.tributario.pagosbeneficios;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

/* HU048: Ventanilla y Rentas consultan los pagos y beneficios de un contribuyente. Contrato en back/HU048_API.md. */
@RestController
@RequestMapping("/api/contribuyentes")
public class PagosBeneficiosController {

    private final PagosBeneficiosService servicio;
    private final SesionInterna sesion;

    public PagosBeneficiosController(PagosBeneficiosService servicio, SesionInterna sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping("/pagos-beneficios")
    public PagosBeneficiosRespuesta consultar(
            @RequestParam @NotBlank(message = "es obligatorio") @Size(max = 20, message = "es demasiado largo")
            String consulta, HttpServletRequest request) {
        sesion.conRol(request, NombreRol.VENTANILLA, NombreRol.ANALISTA_RENTAS, NombreRol.GERENCIA,
                NombreRol.ADMINISTRADOR);
        return servicio.consultar(consulta.trim());
    }
}
