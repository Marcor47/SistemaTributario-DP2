package pe.municipalidad.tributario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.municipalidad.tributario.model.Contribuyente;

import java.util.Optional;

@Repository
public interface ContribuyenteRepository extends JpaRepository<Contribuyente, Long> {

    Optional<Contribuyente> findByCodigoContribuyenteIgnoreCase(String codigoContribuyente);

    Optional<Contribuyente> findByNumeroDocumento(String numeroDocumento);

    @Query("SELECT c FROM Contribuyente c " +
           "WHERE UPPER(TRIM(c.codigoContribuyente)) = UPPER(TRIM(:identificador)) " +
           "   OR UPPER(TRIM(c.numeroDocumento)) = UPPER(TRIM(:identificador))")
    Optional<Contribuyente> findByIdentificador(@Param("identificador") String identificador);
}
