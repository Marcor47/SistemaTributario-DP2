package pe.municipalidad.tributario.constancia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    Optional<TransaccionPago> findByIdOrdenPasarela(String idOrdenPasarela);
}
