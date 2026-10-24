package pe.municipalidad.tributario.ia;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    List<Segmento> findByEstado(EstadoSegmento estado);
}
