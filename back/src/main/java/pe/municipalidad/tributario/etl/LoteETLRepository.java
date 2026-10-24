package pe.municipalidad.tributario.etl;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteETLRepository extends JpaRepository<LoteETL, Long> {

    Optional<LoteETL> findTopByEstadoOrderByFechaCorteDescIdLoteDesc(EstadoLote estado);
}
