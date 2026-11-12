package pe.municipalidad.tributario.documentos;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoBuzonRepository extends JpaRepository<DocumentoBuzon, Long>, JpaSpecificationExecutor<DocumentoBuzon> {

    List<DocumentoBuzon> findByDocumentoContribuyenteOrderByFechaEnvioDesc(String documentoContribuyente);

    long countByDocumentoContribuyenteAndArchivadoFalseAndLeidoActualmenteFalse(String documentoContribuyente);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentoBuzon d where d.idDocumento = :id and d.documentoContribuyente = :documento")
    Optional<DocumentoBuzon> bloquearPropio(@Param("id") Long id, @Param("documento") String documento);
}
