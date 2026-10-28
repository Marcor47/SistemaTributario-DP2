package pe.municipalidad.tributario.reclamo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.municipalidad.tributario.seguridad.Usuario;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    Optional<Reclamo> findByNumeroReclamo(String numeroReclamo);

    /* Reclamos del contribuyente (app móvil) o del trabajador (plataforma), para consultar su estado. */
    List<Reclamo> findByDocumentoContribuyenteOrderByFechaRegistroDesc(String documentoContribuyente);

    List<Reclamo> findByUsuarioReclamanteOrderByFechaRegistroDesc(Usuario usuarioReclamante);

    /* Bandeja del Gestor de reclamos y del encargado de revisión. */
    List<Reclamo> findByEstadoOrderByFechaRegistroAsc(EstadoReclamo estado);

    List<Reclamo> findByResponsableActualAndEstadoOrderByFechaRegistroAsc(Usuario responsable, EstadoReclamo estado);

    /* Número asignado por el sistema: REC-AAAA-NNNNNN. */
    @Query(value = "SELECT operativo.fn_siguiente_numero_reclamo(:fecha)", nativeQuery = true)
    String siguienteNumero(@Param("fecha") LocalDate fecha);
}
