package pe.municipalidad.tributario.etl;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;

/*
 * RNF-018 / RNF-033: auditoría de los accesos a datos personales en claro desde la gestión de datos (bandeja de
 * homologación, reidentificación de seudónimos y mapa de segmentos). Solo lectura: el registro es inalterable.
 */
@Service
@Transactional(readOnly = true)
public class AuditoriaAccesosService {

    static final String ACCION = "REIDENTIFICAR";
    static final int LIMITE_MAXIMO = 500;

    private final RegistroAuditoriaRepository registros;

    public AuditoriaAccesosService(RegistroAuditoriaRepository registros) {
        this.registros = registros;
    }

    public List<AccesoDatoRespuesta> recientes(int limite) {
        int tamanio = Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        return registros.findByAccionOrderByFechaHoraDescIdRegistroDesc(ACCION, PageRequest.of(0, tamanio)).stream()
                .map(AccesoDatoRespuesta::desde)
                .toList();
    }
}
