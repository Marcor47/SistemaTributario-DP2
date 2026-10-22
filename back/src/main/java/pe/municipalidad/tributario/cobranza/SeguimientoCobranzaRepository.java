package pe.municipalidad.tributario.cobranza;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoCobranzaRepository extends JpaRepository<SeguimientoCobranza, Long> {

    List<SeguimientoCobranza> findByObligacionIdOrderByFechaInicioAscIdAsc(Long obligacionId);

    /* Una sola consulta para todas las obligaciones de un listado, en vez de una por obligación. */
    List<SeguimientoCobranza> findByObligacionIdInOrderByFechaInicioAscIdAsc(Collection<Long> obligacionIds);
}
