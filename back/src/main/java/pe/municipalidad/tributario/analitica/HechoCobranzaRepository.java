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
}
