package pe.municipalidad.tributario.declaracionjurada;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;

@RestController
@RequestMapping("/api/predios")
public class DeclaracionJuradaController {

    private final DeclaracionJuradaService service;
    private final SesionInterna sesion;

    public DeclaracionJuradaController(DeclaracionJuradaService service, SesionInterna sesion) {
        this.service = service;
        this.sesion = sesion;
    }

    @GetMapping("/{codigoPredio}/declaracion-jurada")
    public DeclaracionJuradaRespuesta consultar(@PathVariable String codigoPredio, HttpServletRequest request) {
        sesion.conRol(request, NombreRol.ANALISTA_RENTAS, NombreRol.ADMINISTRADOR);
        return service.consultar(codigoPredio);
    }
}
