package pe.municipalidad.tributario.etl;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasoHomologacionRepository extends JpaRepository<CasoHomologacion, Long> {

    List<CasoHomologacion> findByEstado(EstadoCaso estado);
}
