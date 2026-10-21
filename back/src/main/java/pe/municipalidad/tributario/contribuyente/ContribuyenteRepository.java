package pe.municipalidad.tributario.contribuyente;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContribuyenteRepository extends JpaRepository<Contribuyente, String> {

    Optional<Contribuyente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}
