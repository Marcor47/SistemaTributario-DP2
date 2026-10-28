package pe.municipalidad.tributario.etl;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoReglaRepository extends JpaRepository<ResultadoRegla, Long> {

    List<ResultadoRegla> findByLoteIdLote(Long idLote);
}

