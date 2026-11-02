package pe.municipalidad.tributario.constancia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudConstanciaRepository extends JpaRepository<SolicitudConstancia, Long> {

    List<SolicitudConstancia> findByDocumentoContribuyenteOrderByFechaSolicitudDesc(String documentoContribuyente);

    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndEstadoNotInOrderByFechaSolicitudDesc(
            String documentoContribuyente, Collection<EstadoConstancia> estados);

    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndEstadoOrderByFechaSolicitudDesc(
            String documentoContribuyente, EstadoConstancia estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudConstancia s where s.idSolicitud = :id")
    Optional<SolicitudConstancia> bloquearPorId(@Param("id") Long id);
}
