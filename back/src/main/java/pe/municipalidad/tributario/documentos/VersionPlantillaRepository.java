package pe.municipalidad.tributario.documentos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionPlantillaRepository extends JpaRepository<VersionPlantilla, Long> {

    @EntityGraph(attributePaths = "plantilla")
    List<VersionPlantilla> findByVigenteTrueOrderByPlantillaCodigoAsc();

    @EntityGraph(attributePaths = "plantilla")
    Optional<VersionPlantilla> findByPlantillaCodigoAndVigenteTrue(String codigo);

    Optional<VersionPlantilla> findTopByPlantillaCodigoOrderByNumeroVersionDesc(String codigo);

    List<VersionPlantilla> findByPlantillaCodigo(String codigo);
}
