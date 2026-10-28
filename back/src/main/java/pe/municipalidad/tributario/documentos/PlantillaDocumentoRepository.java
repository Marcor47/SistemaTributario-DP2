package pe.municipalidad.tributario.documentos;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantillaDocumentoRepository extends JpaRepository<PlantillaDocumento, Long> {

    Optional<PlantillaDocumento> findByCodigo(String codigo);
}
