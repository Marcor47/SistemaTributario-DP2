package pe.municipalidad.tributario.etl;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteETLRepository extends JpaRepository<LoteETL, Long> {

    Optional<LoteETL> findTopByEstadoOrderByFechaCorteDescIdLoteDesc(EstadoLote estado);

    Optional<LoteETL> findTopByEstadoAndFuenteOrderByFechaCorteDescIdLoteDesc(EstadoLote estado, String fuente);

    /*
     * Última carga publicada de una fuente que realmente trajo hechos. Una carga que solo calculó indicadores
     * no sirve para los reportes que se apoyan en el modelo estrella.
     */
    @Query("""
            select l from LoteETL l
            where l.estado = pe.municipalidad.tributario.etl.EstadoLote.PUBLICADO
              and l.fuente = :fuente
              and exists (select 1 from HechoCobranza h where h.lote = l)
            order by l.fechaCorte desc, l.idLote desc
            """)
    List<LoteETL> buscarPublicadosConHechos(@Param("fuente") String fuente, Pageable pagina);
}
