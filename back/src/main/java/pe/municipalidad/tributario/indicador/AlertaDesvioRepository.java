package pe.municipalidad.tributario.indicador;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaDesvioRepository extends JpaRepository<AlertaDesvio, Long> {

    List<AlertaDesvio> findByActivaTrueOrderByFechaGeneracionDesc();

    /* El filtro es opcional: un indicador nulo no filtra. Se trae lo que muestra el dashboard en una consulta. */
    @Query("""
            select a from AlertaDesvio a
              join fetch a.meta m join fetch m.indicador i
              join fetch a.valorIndicador v join fetch v.lote
            where a.activa = true
              and (:indicadorCodigo is null or i.codigo = :indicadorCodigo)
            order by a.fechaGeneracion desc, i.nombre asc
            """)
    List<AlertaDesvio> buscarActivas(@Param("indicadorCodigo") String indicadorCodigo);

    /* Por meta hay a lo sumo una alerta activa: la de la última carga evaluada. */
    @Query("""
            select a from AlertaDesvio a join fetch a.valorIndicador
            where a.meta = :meta and a.activa = true
            """)
    Optional<AlertaDesvio> buscarActivaDeMeta(@Param("meta") MetaIndicador meta);

    void deleteByMeta(MetaIndicador meta);
}
