package pe.municipalidad.tributario.ia;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    List<Segmento> findByEstado(EstadoSegmento estado);

    /* Con el modelo que los generó, ordenados para mostrarse como filtro (HU031). */
    @Query("""
            select s from Segmento s join fetch s.modelo
            where s.estado = :estado
            order by s.nombre asc, s.idSegmento asc
            """)
    List<Segmento> buscarPorEstado(@Param("estado") EstadoSegmento estado);

    @Query("""
            select s from Segmento s join fetch s.modelo
            order by s.idSegmento desc
            """)
    List<Segmento> buscarTodos();

    @Query("select s from Segmento s join fetch s.modelo where s.idSegmento in :ids")
    List<Segmento> buscarPorIds(@Param("ids") Collection<Long> ids);
}
