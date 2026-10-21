package pe.municipalidad.tributario.obligacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Obligacion> findWithContribuyenteById(Long id);

    /* Vencida = tiene saldo y su fecha de vencimiento es anterior a la fecha indicada. */
    @Query("""
            select o from Obligacion o
            where o.contribuyente.codigo = :contribuyenteCodigo
              and o.fechaVencimiento < :fecha
              and o.montoPagado < o.montoEmitido
            order by o.fechaVencimiento asc, o.tributo asc
            """)
    List<Obligacion> buscarVencidas(@Param("contribuyenteCodigo") String contribuyenteCodigo,
            @Param("fecha") LocalDate fecha);
}
