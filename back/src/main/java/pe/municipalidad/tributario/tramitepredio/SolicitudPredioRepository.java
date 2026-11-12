package pe.municipalidad.tributario.tramitepredio;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudPredioRepository extends JpaRepository<SolicitudPredio, Long> {

    List<SolicitudPredio> findByEstadoOrderByFechaEnvioAsc(EstadoSolicitudPredio estado);
    List<SolicitudPredio> findByDocumentoContribuyenteOrderByFechaEnvioDesc(String documentoContribuyente);

    /* HU043: dos revisores no pueden decidir a la vez la misma solicitud. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPredio s where s.idSolicitud = :id")
    Optional<SolicitudPredio> bloquearPorId(@Param("id") Long id);
}
