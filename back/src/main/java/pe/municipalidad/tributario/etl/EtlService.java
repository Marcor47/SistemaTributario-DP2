package pe.municipalidad.tributario.etl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.ia.SegmentacionModeloService;
import pe.municipalidad.tributario.indicador.IndiceMorosidadService;

/*
 * HU025 y HU026 (RF-038 a RF-041): orquesta el ETL de Rentas. La lógica está en la base (R__etl_pipeline.sql,
 * etl.ejecutar_lote): SAMNET (samnet.ps_etl_*) → bronce → plata (reglas R1 a R5, cuarentena y homologación) → modelo
 * estrella (analitica) y maestros. Un lote es una transacción: si falla, no queda nada a medias.
 *
 * Se ejecuta a demanda (POST /api/etl/lotes) y, si se configura tributario.etl.programacion, cada día con la fecha de
 * corte del día anterior. Después de publicar calcula los indicadores y alertas de la carga nueva, igual que al
 * iniciar el backend (HU008, HU039) y, con tributario.segmentacion.tras-etl, la segmentación en borrador (HU030).
 */
@Service
public class EtlService {

    private static final Logger LOG = LoggerFactory.getLogger(EtlService.class);

    private final JdbcTemplate jdbc;
    private final LoteETLRepository lotes;
    private final IndiceMorosidadService indiceMorosidad;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final String claveContribuyente;
    private final String clavePredio;
    private final BigDecimal maxRechazo;
    private final BigDecimal umbralRevision;
    private final SegmentacionModeloService segmentacion;
    private final boolean segmentarTrasEtl;

    public EtlService(JdbcTemplate jdbc, LoteETLRepository lotes, IndiceMorosidadService indiceMorosidad,
            PlatformTransactionManager transacciones, Clock reloj,
            @Value("${tributario.etl.clave-seudonimo-contribuyente:}") String claveContribuyente,
            @Value("${tributario.etl.clave-seudonimo-predio:}") String clavePredio,
            @Value("${tributario.etl.max-rechazo-porcentaje:10}") BigDecimal maxRechazo,
            @Value("${tributario.etl.umbral-revision:0.65}") BigDecimal umbralRevision,
            SegmentacionModeloService segmentacion,
            @Value("${tributario.segmentacion.tras-etl:false}") boolean segmentarTrasEtl) {
        this.jdbc = jdbc;
        this.lotes = lotes;
        this.indiceMorosidad = indiceMorosidad;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
        this.claveContribuyente = claveContribuyente;
        this.clavePredio = clavePredio;
        this.maxRechazo = maxRechazo;
        this.umbralRevision = umbralRevision;
        this.segmentacion = segmentacion;
        this.segmentarTrasEtl = segmentarTrasEtl;
    }

    public EvaluacionCalidadRespuesta ejecutar(LocalDate fechaCorte, TipoEjecucion tipo) {
        if (claveContribuyente.isBlank() || clavePredio.isBlank()) {
            throw new ReglaNegocioException("ETL_SIN_CLAVES", "Configure tributario.etl.clave-seudonimo-contribuyente"
                    + " y tributario.etl.clave-seudonimo-predio (RNF-018) para ejecutar el ETL.");
        }
        LocalDateTime inicio = LocalDateTime.now(reloj);
        LocalDate corte = fechaCorte == null ? inicio.toLocalDate() : fechaCorte;
        if (corte.isAfter(inicio.toLocalDate())) {
            throw new ReglaNegocioException("FECHA_CORTE_FUTURA",
                    "La fecha de corte " + corte + " es posterior a hoy (R5).");
        }
        Long idLote = transaccion.execute(estado -> jdbc.queryForObject(
                "SELECT etl.ejecutar_lote(?, ?, ?, ?, ?, ?, ?)", Long.class, Date.valueOf(corte), tipo.name(),
                claveContribuyente, clavePredio, Timestamp.valueOf(inicio), maxRechazo, umbralRevision));
        EvaluacionCalidadRespuesta resultado = obtener(idLote);
        LOG.info("ETL: lote {} con corte {} {} ({} leídos, {} rechazados)", idLote, corte, resultado.estado(),
                resultado.registrosLeidos(), resultado.registrosRechazados());
        if (resultado.lotePublicado()) {
            try {
                List<Long> calculadas = indiceMorosidad.calcularCargasPendientes();
                LOG.info("ETL: índice de morosidad calculado para las cargas {}", calculadas);
            } catch (RuntimeException ex) {
                // El lote ya está publicado; el índice se vuelve a intentar al iniciar o con POST /calculos.
                LOG.warn("ETL: no se pudo calcular el índice de morosidad del lote {}", idLote, ex);
            }
            if (segmentarTrasEtl) {
                try {
                    segmentacion.calcular("ETL");
                } catch (RuntimeException ex) {
                    // La segmentación queda como estaba; se puede recalcular con POST /api/segmentos/calculos.
                    LOG.warn("ETL: no se pudo recalcular la segmentación tras el lote {}", idLote, ex);
                }
            }
        }
        return resultado;
    }

    /* Ejecución diaria con la fecha de corte del día anterior; tributario.etl.programacion = "-" la desactiva. */
    @Scheduled(cron = "${tributario.etl.programacion:-}", zone = "America/Lima")
    public void ejecutarProgramado() {
        try {
            ejecutar(LocalDate.now(reloj).minusDays(1), TipoEjecucion.PROGRAMADA);
        } catch (RuntimeException ex) {
            LOG.error("ETL programado: el lote falló y no se publicó nada", ex);
        }
    }

    public EvaluacionCalidadRespuesta obtener(Long idLote) {
        return transaccion.execute(estado -> EvaluacionCalidadRespuesta.desde(lotes.findById(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                        "No existe el lote del ETL con id " + idLote + "."))));
    }

    /* Registros que el lote dejó fuera por una regla de calidad. clave es el código de SAMNET (dato en claro). */
    public List<CuarentenaRespuesta> cuarentena(Long idLote) {
        if (!lotes.existsById(idLote)) {
            throw new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO",
                    "No existe el lote del ETL con id " + idLote + ".");
        }
        return jdbc.query("""
                SELECT entidad, clave_origen, regla, motivo FROM plata.cuarentena
                WHERE id_lote = ? ORDER BY regla, entidad, clave_origen
                """, (rs, i) -> new CuarentenaRespuesta(rs.getString("entidad"), rs.getString("clave_origen"),
                        rs.getString("regla"), rs.getString("motivo")), idLote);
    }

    public record CuarentenaRespuesta(String entidad, String claveOrigen, String regla, String motivo) {
    }
}
