package pe.municipalidad.tributario.etl;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasoHomologacionRepository extends JpaRepository<CasoHomologacion, Long> {

    List<CasoHomologacion> findByEstado(EstadoCaso estado);

    @Query("""
            select c from CasoHomologacion c
              join fetch c.lote
              left join fetch c.maestroCandidato
              left join fetch c.maestroProvisional
            where (:estado is null or c.estado = :estado)
            order by c.idCaso
            """)
    List<CasoHomologacion> listar(@Param("estado") EstadoCaso estado);

    /* Dos responsables no pueden resolver el mismo caso a la vez: el segundo espera y encuentra el caso resuelto. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CasoHomologacion c where c.idCaso = :idCaso")
    Optional<CasoHomologacion> bloquear(@Param("idCaso") Long idCaso);
}
