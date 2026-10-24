package pe.municipalidad.tributario.tramitepredio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudPredioRepository extends JpaRepository<SolicitudPredio, Long> {

    List<SolicitudPredio> findByEstadoOrderByFechaEnvioAsc(EstadoSolicitudPredio estado);
    List<SolicitudPredio> findByDocumentoContribuyenteOrderByFechaEnvioDesc(String documentoContribuyente);
}
