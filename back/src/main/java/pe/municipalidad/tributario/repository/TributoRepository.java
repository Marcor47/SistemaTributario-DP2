package pe.municipalidad.tributario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.municipalidad.tributario.model.Tributo;

import java.util.Optional;

@Repository
public interface TributoRepository extends JpaRepository<Tributo, Long> {
    Optional<Tributo> findByCodigo(String codigo);
}
