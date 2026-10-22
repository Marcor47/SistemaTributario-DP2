package pe.municipalidad.tributario.formularios;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormularioRepository extends JpaRepository<FormularioVersion, Long> {
    List<FormularioVersion> findByVigenteTrueOrderByCodigoAsc();
    Optional<FormularioVersion> findByCodigoAndVigenteTrue(String codigo);
    Optional<FormularioVersion> findTopByCodigoOrderByVersionNumeroDesc(String codigo);
}
