package pe.municipalidad.tributario.indicador;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValorIndicadorRepository extends JpaRepository<ValorIndicador, Long> {

    @Query("""
            select v from ValorIndicador v join fetch v.indicador join fetch v.lote l
            where l.idLote = :idLote
            """)
    List<ValorIndicador> buscarPorLote(@Param("idLote") Long idLote);

    /*
     * Valores que corresponden a la meta (mismo indicador, periodo y alcance) en lotes publicados, del más
     * reciente al más antiguo. Un lote rechazado no cuenta: no afecta los datos ya publicados (RF-038).
     */
    @Query("""
            select v from ValorIndicador v join fetch v.lote l
            where v.indicador.codigo = :indicadorCodigo
              and v.anio = :anio
              and v.numeroPeriodo = :numeroPeriodo
              and v.alcance = :alcance
              and ((:valorAlcance is null and v.valorAlcance is null) or v.valorAlcance = :valorAlcance)
              and l.estado = pe.municipalidad.tributario.etl.EstadoLote.PUBLICADO
            order by l.fechaCorte desc, l.idLote desc
            """)
    List<ValorIndicador> buscarPublicadosDeMeta(@Param("indicadorCodigo") String indicadorCodigo,
            @Param("anio") int anio, @Param("numeroPeriodo") int numeroPeriodo,
            @Param("alcance") AlcanceIndicador alcance, @Param("valorAlcance") String valorAlcance,
            Pageable pagina);

    /* Valores de un indicador en lotes publicados, del más reciente al más antiguo; año y periodo opcionales. */
    @Query("""
            select v from ValorIndicador v join fetch v.lote l
            where v.indicador.codigo = :indicadorCodigo
              and (:anio is null or v.anio = :anio)
              and (:numeroPeriodo is null or v.numeroPeriodo = :numeroPeriodo)
              and l.estado = pe.municipalidad.tributario.etl.EstadoLote.PUBLICADO
            order by l.fechaCorte desc, l.idLote desc, v.anio desc, v.numeroPeriodo desc
            """)
    List<ValorIndicador> buscarPublicadosDeIndicador(@Param("indicadorCodigo") String indicadorCodigo,
            @Param("anio") Integer anio, @Param("numeroPeriodo") Integer numeroPeriodo);
}
