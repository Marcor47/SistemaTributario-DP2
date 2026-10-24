package pe.municipalidad.tributario.reclamo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DerivacionReclamoRepository extends JpaRepository<DerivacionReclamo, Long> {

    List<DerivacionReclamo> findByReclamoIdReclamoOrderByFechaDerivacionAsc(Long idReclamo);
}
