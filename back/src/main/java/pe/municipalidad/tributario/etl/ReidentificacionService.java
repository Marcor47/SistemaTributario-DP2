package pe.municipalidad.tributario.etl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * RF-039, RNF-018: traduce los seudónimos del modelo estrella a las claves de origen de SAMNET.
 * Es la única puerta de reidentificación: toda consulta queda en la auditoría y el log no expone
 * la correspondencia. El modelo analítico sigue sin guardar DNI, RUC, nombres ni domicilios.
 */
@Service
public class ReidentificacionService {

    public static final String SISTEMA_ORIGEN = "SAMNET";

    private static final Logger LOG = LoggerFactory.getLogger(ReidentificacionService.class);

    private final CorrespondenciaOrigenRepository correspondencias;
    private final RegistroAuditoriaRepository auditorias;
    private final Clock reloj;

    public ReidentificacionService(CorrespondenciaOrigenRepository correspondencias,
            RegistroAuditoriaRepository auditorias, Clock reloj) {
        this.correspondencias = correspondencias;
        this.auditorias = auditorias;
        this.reloj = reloj;
    }

    /*
     * Devuelve seudónimo -> clave de origen. Los seudónimos sin correspondencia no aparecen en el mapa:
     * el llamador decide si omite la fila o la deja fuera del reporte.
     */
    @Transactional
    public Map<String, String> clavesDe(Collection<String> seudonimos, TipoEntidad tipoEntidad, Usuario usuario) {
        if (seudonimos == null || seudonimos.isEmpty()) {
            return Map.of();
        }
        Map<String, String> claves = new LinkedHashMap<>();
        List<CorrespondenciaOrigen> filas = correspondencias.buscarPorSeudonimos(seudonimos, tipoEntidad,
                SISTEMA_ORIGEN);
        for (CorrespondenciaOrigen fila : filas) {
            String seudonimo = fila.getEntidadMaestra().getSeudonimo();
            if (claves.putIfAbsent(seudonimo, fila.getClaveOrigen()) == null) {
                auditorias.save(new RegistroAuditoria(usuario, "REIDENTIFICAR", "EntidadMaestra", null,
                        tipoEntidad + ":" + seudonimo, LocalDateTime.now(reloj)));
            }
        }
        if (claves.size() < seudonimos.size()) {
            LOG.warn("No se pudieron reidentificar {} de {} seudónimos de {}", seudonimos.size() - claves.size(),
                    seudonimos.size(), tipoEntidad);
        }
        return claves;
    }
}
