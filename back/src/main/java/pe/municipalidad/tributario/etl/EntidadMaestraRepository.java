package pe.municipalidad.tributario.etl;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntidadMaestraRepository extends JpaRepository<EntidadMaestra, Long> {

    Optional<EntidadMaestra> findBySeudonimo(String seudonimo);
}
