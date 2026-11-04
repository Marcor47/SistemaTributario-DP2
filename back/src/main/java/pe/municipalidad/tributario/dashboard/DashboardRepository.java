package pe.municipalidad.tributario.dashboard;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.municipalidad.tributario.analitica.HechoCobranza;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Consultas del dashboard ejecutivo (HU011) sobre el modelo estrella de una carga del ETL. Se toman las cuotas del
 * año que ya vencieron a la fecha de corte (exigibles), igual que el índice de morosidad (HU008). Los filtros son
 * opcionales: un parámetro nulo no filtra. El segmento filtra a los contribuyentes asignados a él.
 */
public interface DashboardRepository extends Repository<HechoCobranza, Long> {

    /* Una carga que solo trae valores de indicadores no sirve: el dashboard se calcula sobre los hechos. */
    @Query("""
            select l from LoteETL l
            where l.estado = pe.municipalidad.tributario.etl.EstadoLote.PUBLICADO
              and exists (select 1 from HechoCobranza h where h.lote = l)
            order by l.fechaCorte desc, l.idLote desc
            """)
    List<LoteETL> buscarCargasPublicadasConHechos(Pageable pagina);

    @Query("""
            select new pe.municipalidad.tributario.dashboard.MontosDashboard(h.tributo, p.zona,
                       sum(h.montoEmitido), sum(h.montoPagado),
                       coalesce(sum(h.montoInsoluto) filter (where h.estadoPago
                                    <> pe.municipalidad.tributario.obligacion.EstadoObligacion.PAGADA), 0))
            from HechoCobranza h join h.predio p
            where h.lote.idLote = :idLote
              and h.anio = :anio
              and h.fechaVencimiento < :fechaCorte
              and (:zona is null or p.zona = :zona)
              and (:tributo is null or h.tributo = :tributo)
              and (:idSegmento is null or exists (
                       select 1 from AsignacionSegmento a
                       where a.contribuyente = h.contribuyente and a.segmento.idSegmento = :idSegmento))
            group by h.tributo, p.zona
            """)
    List<MontosDashboard> sumarPorTributoYZona(@Param("idLote") Long idLote, @Param("anio") int anio,
            @Param("fechaCorte") LocalDate fechaCorte, @Param("zona") String zona, @Param("tributo") Tributo tributo,
            @Param("idSegmento") Long idSegmento);

    /* Solo segmentos publicados (HU031); un contribuyente se cuenta una vez por segmento. */
    @Query("""
            select new pe.municipalidad.tributario.dashboard.MontosSegmento(s.idSegmento, s.nombre,
                       count(distinct h.contribuyente.seudonimoContribuyente),
                       sum(h.montoEmitido), sum(h.montoPagado),
                       coalesce(sum(h.montoInsoluto) filter (where h.estadoPago
                                    <> pe.municipalidad.tributario.obligacion.EstadoObligacion.PAGADA), 0))
            from HechoCobranza h join h.predio p, AsignacionSegmento a join a.segmento s
            where a.contribuyente = h.contribuyente
              and s.estado = pe.municipalidad.tributario.ia.EstadoSegmento.PUBLICADO
              and h.lote.idLote = :idLote
              and h.anio = :anio
              and h.fechaVencimiento < :fechaCorte
              and (:zona is null or p.zona = :zona)
              and (:tributo is null or h.tributo = :tributo)
              and (:idSegmento is null or s.idSegmento = :idSegmento)
            group by s.idSegmento, s.nombre
            order by s.nombre asc
            """)
    List<MontosSegmento> sumarPorSegmento(@Param("idLote") Long idLote, @Param("anio") int anio,
            @Param("fechaCorte") LocalDate fechaCorte, @Param("zona") String zona, @Param("tributo") Tributo tributo,
            @Param("idSegmento") Long idSegmento);

    @Query("""
            select distinct p.zona from HechoCobranza h join h.predio p
            where h.lote.idLote = :idLote
            order by p.zona
            """)
    List<String> listarZonas(@Param("idLote") Long idLote);

    @Query("""
            select distinct h.anio from HechoCobranza h
            where h.lote.idLote = :idLote
            order by h.anio desc
            """)
    List<Integer> listarAnios(@Param("idLote") Long idLote);
}
