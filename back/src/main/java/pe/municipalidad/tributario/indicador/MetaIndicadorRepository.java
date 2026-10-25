package pe.municipalidad.tributario.indicador;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaIndicadorRepository extends JpaRepository<MetaIndicador, Long> {

    boolean existsByIndicadorCodigoAndAnioAndNumeroPeriodo(String indicadorCodigo, int anio, int numeroPeriodo);

    Optional<MetaIndicador> findByIndicadorCodigoAndAnioAndNumeroPeriodo(String indicadorCodigo, int anio,
            int numeroPeriodo);

    /* Los filtros son opcionales: un parámetro nulo no filtra. Se trae el indicador en la misma consulta. */
    @Query("""
            select m from MetaIndicador m join fetch m.indicador i
            where (:indicadorCodigo is null or i.codigo = :indicadorCodigo)
              and (:anio is null or m.anio = :anio)
            order by m.anio desc, i.nombre asc, m.numeroPeriodo asc
            """)
    List<MetaIndicador> buscar(@Param("indicadorCodigo") String indicadorCodigo, @Param("anio") Integer anio);
}
