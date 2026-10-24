package pe.municipalidad.tributario.reclamo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaMunicipalRepository extends JpaRepository<AreaMunicipal, Long> {

    Optional<AreaMunicipal> findByCodigo(String codigo);

    List<AreaMunicipal> findByActivaTrueOrderByNombreAsc();
}
