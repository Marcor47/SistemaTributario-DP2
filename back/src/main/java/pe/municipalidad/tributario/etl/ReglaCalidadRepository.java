package pe.municipalidad.tributario.etl;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglaCalidadRepository extends JpaRepository<ReglaCalidad, Long> {

    Optional<ReglaCalidad> findByCodigo(String codigo);
}
