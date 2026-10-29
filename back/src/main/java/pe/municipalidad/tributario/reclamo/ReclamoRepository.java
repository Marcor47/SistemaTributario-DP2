package pe.municipalidad.tributario.reclamo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.municipalidad.tributario.seguridad.Usuario;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    Optional<Reclamo> findByNumeroReclamo(String numeroReclamo);

    /* Reclamos del contribuyente (app móvil) o del trabajador (plataforma), para consultar su estado. */
    @EntityGraph(attributePaths = { "derivaciones", "derivaciones.areaDestino", "derivaciones.responsable",
            "derivaciones.derivadoPor" })
    @Query("SELECT DISTINCT r FROM Reclamo r WHERE r.documentoContribuyente = :documento ORDER BY r.fechaRegistro DESC")
    List<Reclamo> buscarDelContribuyente(@Param("documento") String documentoContribuyente);

    @EntityGraph(attributePaths = { "derivaciones", "derivaciones.areaDestino", "derivaciones.responsable",
            "derivaciones.derivadoPor" })
    @Query("SELECT DISTINCT r FROM Reclamo r WHERE r.usuarioReclamante = :usuario ORDER BY r.fechaRegistro DESC")
    List<Reclamo> buscarDelTrabajador(@Param("usuario") Usuario usuarioReclamante);

    @EntityGraph(attributePaths = { "usuarioReclamante", "areaActual", "responsableActual", "derivaciones",
            "derivaciones.areaDestino", "derivaciones.responsable", "derivaciones.derivadoPor" })
    @Query("SELECT DISTINCT r FROM Reclamo r ORDER BY r.fechaRegistro DESC")
    List<Reclamo> buscarTodosParaGestion();

    @EntityGraph(attributePaths = { "usuarioReclamante", "areaActual", "responsableActual", "derivaciones",
            "derivaciones.areaDestino", "derivaciones.responsable", "derivaciones.derivadoPor" })
    @Query("SELECT DISTINCT r FROM Reclamo r WHERE r.responsableActual = :responsable ORDER BY r.fechaRegistro DESC")
    List<Reclamo> buscarAsignadosA(@Param("responsable") Usuario responsable);

    @EntityGraph(attributePaths = { "usuarioReclamante", "areaActual", "responsableActual", "derivaciones",
            "derivaciones.areaDestino", "derivaciones.responsable", "derivaciones.derivadoPor" })
    @Query("SELECT r FROM Reclamo r WHERE r.idReclamo = :id")
    Optional<Reclamo> buscarDetalle(@Param("id") Long idReclamo);

    /* Bandeja del Gestor de reclamos y del encargado de revisión. */
    List<Reclamo> findByEstadoOrderByFechaRegistroAsc(EstadoReclamo estado);

    List<Reclamo> findByResponsableActualAndEstadoOrderByFechaRegistroAsc(Usuario responsable, EstadoReclamo estado);

    /* Número asignado por el sistema: REC-AAAA-NNNNNN. */
    @Query(value = "SELECT operativo.fn_siguiente_numero_reclamo(:fecha)", nativeQuery = true)
    String siguienteNumero(@Param("fecha") LocalDate fecha);
}
