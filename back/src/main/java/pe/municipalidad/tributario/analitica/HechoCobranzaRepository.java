package pe.municipalidad.tributario.analitica;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HechoCobranzaRepository extends JpaRepository<HechoCobranza, Long> {

    /*
     * Cuotas del año que ya vencieron a la fecha de corte (exigibles): una cuota que vence el mismo día de corte
     * aún no está vencida. Lo no pagado es el insoluto de las que no están pagadas.
     */
    @Query("""
            select new pe.municipalidad.tributario.analitica.MontosMorosidad(h.tributo, p.zona,
                       sum(h.montoEmitido),
                       coalesce(sum(h.montoInsoluto) filter (where h.estadoPago
                                    <> pe.municipalidad.tributario.obligacion.EstadoObligacion.PAGADA), 0))
            from HechoCobranza h join h.predio p
            where h.lote.idLote = :idLote
              and h.anio = :anio
              and h.fechaVencimiento < :fechaCorte
            group by h.tributo, p.zona
            """)
    List<MontosMorosidad> sumarCuotasExigibles(@Param("idLote") Long idLote, @Param("anio") int anio,
            @Param("fechaCorte") LocalDate fechaCorte);

    /*
     * Hechos vencidos que aún tienen saldo sin beneficio (emitido - pagado > 0). Incluye las cuotas que un
     * beneficio dejó en cero para que el detalle coincida con ps_reportecartera; la deuda solo sumará las que
     * conservan insoluto. El contribuyente y el predio se traen en la misma consulta.
     */
    @Query("""
            select h from HechoCobranza h
              join fetch h.contribuyente c
              join fetch h.predio p
            where h.lote.idLote = :idLote
              and h.fechaVencimiento < :fechaCorte
              and h.montoEmitido > h.montoPagado
            order by c.seudonimoContribuyente, h.tributo, h.anio, h.cuota
            """)
    List<HechoCobranza> buscarVencidasConSaldo(@Param("idLote") Long idLote,
            @Param("fechaCorte") LocalDate fechaCorte);

    /*
     * HU037: todos los hechos de una carga, incluidos los pagados, porque PRICOS necesita la emisión del año
     * aunque la cuota ya esté cancelada. El contribuyente y el predio se traen en la misma consulta.
     */
    @Query("""
            select h from HechoCobranza h
              join fetch h.contribuyente c
              join fetch h.predio p
            where h.lote.idLote = :idLote
            order by c.seudonimoContribuyente, h.anio, h.tributo, h.cuota
            """)
    List<HechoCobranza> buscarPorLote(@Param("idLote") Long idLote);

    /* HU018: zonas disponibles en una carga, para el selector del reporte de morosos. */
    @Query("""
            select distinct p.zona from HechoCobranza h join h.predio p
            where h.lote.idLote = :idLote
            order by p.zona
            """)
    List<String> listarZonas(@Param("idLote") Long idLote);
}
