package pe.municipalidad.tributario.ia;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsignacionSegmentoRepository extends JpaRepository<AsignacionSegmento, Long> {

    /* HU045: última asignación de un segmento publicado para cada contribuyente seudonimizado. */
    @Query("""
            select a from AsignacionSegmento a
              join fetch a.segmento s
              join fetch a.contribuyente c
            where s.estado = :estado
              and c.seudonimoContribuyente in :seudonimos
            order by c.seudonimoContribuyente asc, a.fechaAsignacion desc, s.idSegmento desc
            """)
    List<AsignacionSegmento> publicadosPorSeudonimos(@Param("seudonimos") Collection<String> seudonimos,
            @Param("estado") EstadoSegmento estado);
}
