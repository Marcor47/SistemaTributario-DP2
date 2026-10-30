package pe.municipalidad.tributario.constancia;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    Optional<TransaccionPago> findByIdOrdenPasarela(String idOrdenPasarela);

    Optional<TransaccionPago> findBySolicitudConstancia_IdSolicitud(Long idSolicitud);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransaccionPago t where t.idTransaccion = :id")
    Optional<TransaccionPago> bloquearPorId(@Param("id") Long id);
}
