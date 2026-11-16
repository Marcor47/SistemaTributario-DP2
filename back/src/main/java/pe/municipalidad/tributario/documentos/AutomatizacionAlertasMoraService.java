package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;

/** Despacha en bloques las cuotas que el ETL dejó en bronce, sin releer el padrón completo. */
@Service
public class AutomatizacionAlertasMoraService {
    private static final Logger LOG = LoggerFactory.getLogger(AutomatizacionAlertasMoraService.class);
    private static final int BLOQUE = 50;

    private final JdbcTemplate jdbc;
    private final AlertaMoraService alertas;
    private final Clock reloj;
    private final CorreoGateway correo;

    public record Configuracion(boolean habilitado, long ultimoLoteDetectado, LocalDateTime actualizadoEn,
            String actualizadoPor, boolean correoReal) {}

    public record Proceso(long idLote, int ultimaFila, String estado, LocalDateTime actualizadoEn,
            LocalDateTime fechaLimite) {}

    public AutomatizacionAlertasMoraService(JdbcTemplate jdbc, AlertaMoraService alertas, Clock reloj,
            CorreoGateway correo) {
        this.jdbc = jdbc;
        this.alertas = alertas;
        this.reloj = reloj;
        this.correo = correo;
    }

    public Configuracion configuracion() {
        return jdbc.queryForObject("""
                SELECT habilitado, ultimo_lote_detectado, actualizado_en, actualizado_por
                FROM operativo.configuracion_alertas_mora WHERE id = 1
                """, (rs, n) -> new Configuracion(rs.getBoolean(1), rs.getLong(2),
                        rs.getTimestamp(3).toLocalDateTime(), rs.getString(4), !correo.registraSinEnviar()));
    }

    /** Al activar, toma como frontera el último lote existente. Un trabajo pausado conserva su progreso. */
    @Transactional
    public Configuracion cambiarEstado(boolean habilitado, String usuario) {
        Boolean anterior = jdbc.queryForObject("""
                SELECT habilitado FROM operativo.configuracion_alertas_mora WHERE id = 1 FOR UPDATE
                """, Boolean.class);
        if (anterior == null || anterior != habilitado) {
            jdbc.update("""
                    UPDATE operativo.configuracion_alertas_mora
                    SET habilitado = ?, ultimo_lote_detectado = CASE WHEN ? THEN
                        (SELECT COALESCE(MAX(id_lote), 0) FROM analitica.lote_etl)
                        ELSE ultimo_lote_detectado END,
                        actualizado_en = ?, actualizado_por = ? WHERE id = 1
                    """, habilitado, habilitado, LocalDateTime.now(reloj), usuario);
            LOG.info("Alertas de mora automáticas {} por {}", habilitado ? "activadas" : "desactivadas", usuario);
        }
        return configuracion();
    }

    public List<Proceso> procesos() {
        return jdbc.query("""
                SELECT p.id_lote, p.ultima_fila, p.estado, p.actualizado_en,
                       l.fecha_fin + INTERVAL '24 hours'
                FROM operativo.proceso_alertas_mora p
                JOIN analitica.lote_etl l ON l.id_lote = p.id_lote
                ORDER BY p.id_lote DESC LIMIT 20
                """, (rs, n) -> new Proceso(rs.getLong(1), rs.getInt(2), rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toLocalDateTime()));
    }

    /** La búsqueda periódica recupera cargas publicadas aun si la aplicación se reinició. */
    @Scheduled(fixedDelayString = "${tributario.alertas-mora.intervalo-ms:1000}", scheduler = "alertasMoraScheduler")
    @Transactional
    public void procesarPendientes() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT habilitado FROM operativo.configuracion_alertas_mora WHERE id = 1 FOR UPDATE
                """, Boolean.class))) return;

        long cursor = jdbc.queryForObject("""
                SELECT ultimo_lote_detectado FROM operativo.configuracion_alertas_mora WHERE id = 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO operativo.proceso_alertas_mora (id_lote)
                SELECT id_lote FROM analitica.lote_etl
                WHERE id_lote > ? AND estado = 'PUBLICADO' AND fuente = 'ps_etl_cobranza'
                ON CONFLICT (id_lote) DO NOTHING
                """, cursor);
        // No saltar un lote EN_CURSO aunque ya exista otro posterior publicado.
        jdbc.update("""
                UPDATE operativo.configuracion_alertas_mora SET ultimo_lote_detectado =
                    COALESCE((SELECT MAX(id_lote) FROM analitica.lote_etl WHERE id_lote > ?
                        AND estado <> 'EN_CURSO' AND id_lote < COALESCE(
                            (SELECT MIN(id_lote) FROM analitica.lote_etl
                             WHERE id_lote > ? AND estado = 'EN_CURSO'), 9223372036854775807)),
                        ultimo_lote_detectado)
                WHERE id = 1
                """, cursor, cursor);

        List<Proceso> pendientes = jdbc.query("""
                SELECT p.id_lote, p.ultima_fila, p.estado, p.actualizado_en,
                       l.fecha_fin + INTERVAL '24 hours'
                FROM operativo.proceso_alertas_mora p
                JOIN analitica.lote_etl l ON l.id_lote = p.id_lote
                WHERE p.estado IN ('PENDIENTE', 'EN_CURSO')
                   OR (p.estado = 'CON_ERRORES' AND p.proximo_reintento <= now())
                ORDER BY p.id_lote LIMIT 1 FOR UPDATE OF p SKIP LOCKED
                """, (rs, n) -> new Proceso(rs.getLong(1), rs.getInt(2), rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toLocalDateTime()));
        if (pendientes.isEmpty()) return;
        Proceso proceso = pendientes.getFirst();
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT b.fila, b.id_obligacion, b.fecha_vencimiento, b.insoluto,
                       c.correo, c.nombre, c.numero_documento
                FROM bronce.cobranza b
                LEFT JOIN samnet.contribuyente c ON c.codigo = b.contribuyente_codigo
                WHERE b.id_lote = ? AND b.fila > ? ORDER BY b.fila LIMIT ?
                """, proceso.idLote(), proceso.ultimaFila(), BLOQUE);
        if (!filas.isEmpty()) {
            alertas.procesarAlertasLote(new ProcesarAlertasMoraSolicitud(proceso.idLote(), filas));
            int ultimaFila = ((Number) filas.getLast().get("fila")).intValue();
            jdbc.update("""
                    UPDATE operativo.proceso_alertas_mora
                    SET ultima_fila = ?, estado = 'EN_CURSO', actualizado_en = ? WHERE id_lote = ?
                    """, ultimaFila, LocalDateTime.now(reloj), proceso.idLote());
            return;
        }
        // Los fallos SMTP quedan en historial y se vuelven a intentar sin bloquear las demás cuotas.
        List<Map<String, Object>> fallidos = jdbc.queryForList("""
                SELECT b.fila, b.id_obligacion, b.fecha_vencimiento, b.insoluto,
                       c.correo, c.nombre, c.numero_documento
                FROM operativo.aviso_correo a
                JOIN bronce.cobranza b ON b.id_lote = a.id_lote
                    AND b.id_obligacion::text = a.id_obligacion
                LEFT JOIN samnet.contribuyente c ON c.codigo = b.contribuyente_codigo
                WHERE a.id_lote = ? AND a.tipo = 'MORA_TEMPRANA' AND a.estado = 'FALLIDO'
                ORDER BY b.fila LIMIT ?
                """, proceso.idLote(), BLOQUE);
        if (!fallidos.isEmpty()) {
            alertas.procesarAlertasLote(new ProcesarAlertasMoraSolicitud(proceso.idLote(), fallidos));
        }
        Integer quedan = jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE id_lote = ? AND tipo = 'MORA_TEMPRANA' AND estado = 'FALLIDO'
                """, Integer.class, proceso.idLote());
        boolean conErrores = quedan != null && quedan > 0;
        jdbc.update("""
                UPDATE operativo.proceso_alertas_mora
                SET estado = ?, proximo_reintento = ?, actualizado_en = ? WHERE id_lote = ?
                """, conErrores ? "CON_ERRORES" : "COMPLETADO",
                conErrores ? LocalDateTime.now(reloj).plusMinutes(5) : null,
                LocalDateTime.now(reloj), proceso.idLote());
    }
}
