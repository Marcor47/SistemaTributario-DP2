package pe.municipalidad.tributario.documentos;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvisoCorreoRepository extends JpaRepository<AvisoCorreo, Long> {

    List<AvisoCorreo> findByEstadoAndFechaProgramadaLessThanEqual(EstadoAviso estado, LocalDateTime hasta);
}
