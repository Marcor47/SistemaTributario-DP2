package pe.municipalidad.tributario.etl;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionHomologacionRepository extends JpaRepository<DecisionHomologacion, Long> {

    List<DecisionHomologacion> findByEntidadMaestraIdMaestroOrderByFechaHoraAsc(Long idMaestro);
}
