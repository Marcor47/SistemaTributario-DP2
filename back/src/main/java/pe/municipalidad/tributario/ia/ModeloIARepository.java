package pe.municipalidad.tributario.ia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloIARepository extends JpaRepository<ModeloIA, Long> {

    Optional<ModeloIA> findByNombreAndVersion(String nombre, String version);
}
