package pe.municipalidad.tributario.documentos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoBuzonRepository extends JpaRepository<DocumentoBuzon, Long> {

    List<DocumentoBuzon> findByDocumentoContribuyenteOrderByFechaEnvioDesc(String documentoContribuyente);
}
