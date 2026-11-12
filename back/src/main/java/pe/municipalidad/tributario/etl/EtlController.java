package pe.municipalidad.tributario.etl;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
 * HU025 (RF-038): ejecutar el ETL a demanda y revisar su cuarentena. Requiere ETL:GESTIONAR (Responsable de datos),
 * como el resto de /api/etl. Solo API; la pantalla queda pendiente.
 */
@RestController
@RequestMapping("/api/etl/lotes")
public class EtlController {

    private final EtlService etl;

    public EtlController(EtlService etl) {
        this.etl = etl;
    }

    /* Sin cuerpo o sin fecha: fecha de corte de hoy. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluacionCalidadRespuesta ejecutar(@RequestBody(required = false) EjecutarLotePeticion peticion) {
        return etl.ejecutar(peticion == null ? null : peticion.fechaCorte(), TipoEjecucion.A_DEMANDA);
    }

    @GetMapping("/{idLote}")
    public EvaluacionCalidadRespuesta obtener(@PathVariable Long idLote) {
        return etl.obtener(idLote);
    }

    @GetMapping("/{idLote}/cuarentena")
    public List<EtlService.CuarentenaRespuesta> cuarentena(@PathVariable Long idLote) {
        return etl.cuarentena(idLote);
    }

    public record EjecutarLotePeticion(LocalDate fechaCorte) {
    }
}
