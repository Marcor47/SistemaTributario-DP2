package pe.municipalidad.tributario.ia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumenEjecutivoRepository extends JpaRepository<ResumenEjecutivo, Long> {

    /* El último resumen de un periodo para una carga: evita volver a consultar el modelo por lo mismo. */
    Optional<ResumenEjecutivo> findFirstByPeriodoAndIdLoteOrderByFechaGeneracionDesc(String periodo, Long idLote);
}
