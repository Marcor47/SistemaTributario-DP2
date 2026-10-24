package pe.municipalidad.tributario.constancia;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudConstanciaRepository extends JpaRepository<SolicitudConstancia, Long> {

    List<SolicitudConstancia> findByDocumentoContribuyenteOrderByFechaSolicitudDesc(String documentoContribuyente);
}
