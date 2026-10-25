package pe.municipalidad.tributario.constancia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudConstanciaRepository extends JpaRepository<SolicitudConstancia, Long> {

    List<SolicitudConstancia> findByDocumentoContribuyenteOrderByFechaSolicitudDesc(String documentoContribuyente);

    Optional<SolicitudConstancia> findFirstByDocumentoContribuyenteAndEstadoNotInOrderByFechaSolicitudDesc(
            String documentoContribuyente, Collection<EstadoConstancia> estados);
}
