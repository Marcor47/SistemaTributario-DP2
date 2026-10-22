package pe.municipalidad.tributario.indicador;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicadorRepository extends JpaRepository<Indicador, String> {

    List<Indicador> findByActivoTrueOrderByNombreAsc();
}
