package pe.municipalidad.tributario.indicador;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaDesvioRepository extends JpaRepository<AlertaDesvio, Long> {

    List<AlertaDesvio> findByActivaTrueOrderByFechaGeneracionDesc();
}
