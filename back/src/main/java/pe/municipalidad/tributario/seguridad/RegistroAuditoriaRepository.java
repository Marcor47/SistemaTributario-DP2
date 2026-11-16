package pe.municipalidad.tributario.seguridad;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /* Lo más reciente primero, con el usuario cargado (la auditoría de accesos lo muestra). */
    @EntityGraph(attributePaths = "usuario")
    List<RegistroAuditoria> findByAccionOrderByFechaHoraDescIdRegistroDesc(String accion, Pageable pagina);
}
