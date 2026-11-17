package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import pe.municipalidad.tributario.integracion.correo.RecordatorioCorreoHtml;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracion;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracionRepository;

/** HU016: recordatorios previos al vencimiento, con deduplicación por obligación y preferencia del contribuyente. */
@Service
public class RecordatorioVencimientoService {
    public record Resultado(LocalDate fecha, int diasAnticipacion, int detectados, int enviados,
                            int sinCorreo, int desactivados, int duplicados, int fallidos, int registrados,
                            boolean sinServidorCorreo) {}

    public record Configuracion(int diasAnticipacion, int minimo, int maximo, int valorPorDefecto,
                                LocalDateTime actualizadoEn, String actualizadoPor) {}

    /* Estado del recordatorio de una cuota: el último aviso registrado o, si aún no hay, cuándo corresponde. */
    public record EstadoRecordatorio(Long idAviso, String idObligacion, String estado, String correoDestino,
                                     LocalDateTime fechaProgramada, LocalDateTime fechaEnvio, int diasAnticipacion) {}

    private static final int DIAS_MINIMO = 1;
    private static final int DIAS_MAXIMO = 10;
    private static final int DIAS_POR_DEFECTO = 5;
    private static final LocalTime HORA_ENVIO = LocalTime.of(8, 0);

    private final SamnetGateway samnet;
    private final AvisoCorreoRepository avisos;
    private final PreferenciaAvisoRepository preferencias;
    private final CorreoGateway correo;
    private final RecordatorioCorreoHtml correoHtml;
    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final ParametroConfiguracionRepository parametros;

    public RecordatorioVencimientoService(SamnetGateway samnet, AvisoCorreoRepository avisos,
            PreferenciaAvisoRepository preferencias, CorreoGateway correo, RecordatorioCorreoHtml correoHtml,
            JdbcTemplate jdbc, Clock reloj, ParametroConfiguracionRepository parametros) {
        this.samnet = samnet;
        this.avisos = avisos;
        this.preferencias = preferencias;
        this.correo = correo;
        this.correoHtml = correoHtml;
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.parametros = parametros;
    }

    @Transactional
    public Resultado procesar() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, 160016L))) {
            throw new ReglaNegocioException("RECORDATORIOS_EN_CURSO", "Ya se están procesando recordatorios.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int dias = anticipacion();
        var pendientes = samnet.obtenerVencimientos(hoy.plusDays(1), hoy.plusDays(dias));
        Map<String, Boolean> recordatorioActivo = preferencias.findAllById(pendientes.stream()
                .map(fila -> texto(fila.get("codigo_contribuyente"))).filter(Objects::nonNull)
                .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PreferenciaAviso::getCodigoContribuyente,
                        PreferenciaAviso::isRecordatorioVencimiento));
        int enviados = 0, sinCorreo = 0, desactivados = 0, duplicados = 0, fallidos = 0, registrados = 0;
        for (Map<String, Object> fila : pendientes) {
            String id = String.valueOf(fila.get("id_obligacion"));
            String codigo = texto(fila.get("codigo_contribuyente"));
            String destino = texto(fila.get("correo"));
            String documento = texto(fila.get("numero_documento"));
            if (destino == null || documento == null) { sinCorreo++; continue; }
            if (codigo != null && Boolean.FALSE.equals(recordatorioActivo.get(codigo))) {
                desactivados++;
                continue;
            }
            if (avisos.existsByTipoAndIdObligacionAndEstado(TipoAviso.RECORDATORIO_VENCIMIENTO, id, EstadoAviso.ENVIADO)
                    || (correo.registraSinEnviar() && avisos.existsByTipoAndIdObligacionAndEstado(
                            TipoAviso.RECORDATORIO_VENCIMIENTO, id, EstadoAviso.REGISTRADO))) {
                duplicados++;
                continue;
            }
            String tributo = etiquetaTributo(texto(fila.get("tributo")));
            String nombre = texto(fila.get("nombre"));
            RecordatorioCorreoHtml.Contenido contenido = correoHtml.construir(nombre, id, tributo,
                    texto(fila.get("fecha_vencimiento")), texto(fila.get("saldo")));
            AvisoCorreo aviso = new AvisoCorreo(TipoAviso.RECORDATORIO_VENCIMIENTO, documento, destino,
                    ahora, null, id);
            aviso.enviar(correo, contenido.asunto(), contenido.texto(), contenido.html(), ahora);
            avisos.save(aviso);
            if (aviso.getEstado() == EstadoAviso.ENVIADO) enviados++;
            else if (aviso.getEstado() == EstadoAviso.REGISTRADO) registrados++;
            else fallidos++;
        }
        return new Resultado(hoy, dias, pendientes.size(), enviados, sinCorreo, desactivados, duplicados, fallidos,
                registrados, correo.registraSinEnviar());
    }

    private static String texto(Object valor) {
        if (valor == null || valor.toString().isBlank()) return null;
        return valor.toString().trim();
    }

    /* El SP devuelve el código del tributo; el correo lo presenta como texto legible. */
    private static String etiquetaTributo(String valor) {
        if (valor == null) return null;
        String texto = valor.replace('_', ' ').toLowerCase();
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    @Transactional(readOnly = true)
    public Configuracion configuracion() {
        return new Configuracion(anticipacion(), DIAS_MINIMO, DIAS_MAXIMO, DIAS_POR_DEFECTO, ultimoCambio(),
                ultimoResponsable());
    }

    @Transactional
    public Configuracion guardarConfiguracion(int dias, String usuario) {
        if (dias < DIAS_MINIMO || dias > DIAS_MAXIMO) {
            throw new ReglaNegocioException("ANTICIPACION_INVALIDA", "La anticipación debe estar entre 1 y 10 días.");
        }
        ParametroConfiguracion parametro = parametros.findById(ParametroConfiguracion.DIAS_RECORDATORIO)
                .orElseThrow(() -> new ReglaNegocioException("PARAMETRO_NO_DISPONIBLE",
                        "No está registrado el parámetro de anticipación de recordatorios."));
        String anterior = parametro.getValor();
        parametro.actualizar(String.valueOf(dias));
        jdbc.update("""
                INSERT INTO seguridad.registro_auditoria (id_usuario, accion, entidad, valor_anterior, valor_nuevo, fecha_hora)
                SELECT u.id_usuario, 'CAMBIAR_DIAS_RECORDATORIO', 'ParametroConfiguracion', ?, ?, ?
                FROM seguridad.usuario u WHERE lower(u.nombre_usuario) = lower(?)
                """, anterior, String.valueOf(dias), LocalDateTime.now(reloj), usuario);
        return new Configuracion(dias, DIAS_MINIMO, DIAS_MAXIMO, DIAS_POR_DEFECTO, LocalDateTime.now(reloj),
                nombreDe(usuario));
    }

    @Transactional(readOnly = true)
    public EstadoRecordatorio estadoDe(long idObligacion) {
        int dias = anticipacion();
        String id = String.valueOf(idObligacion);
        var ultimo = jdbc.query("""
                SELECT id_aviso, estado, correo_destino, fecha_programada, fecha_envio
                FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ?
                ORDER BY fecha_programada DESC, id_aviso DESC LIMIT 1
                """, (rs, i) -> new EstadoRecordatorio(rs.getLong("id_aviso"), id, rs.getString("estado"),
                rs.getString("correo_destino"), rs.getTimestamp("fecha_programada").toLocalDateTime(),
                rs.getTimestamp("fecha_envio") == null ? null : rs.getTimestamp("fecha_envio").toLocalDateTime(),
                dias), id);
        if (!ultimo.isEmpty()) {
            return ultimo.get(0);
        }
        var obligacion = samnet.obtenerObligacion(idObligacion).orElseThrow(() -> new ReglaNegocioException(
                "OBLIGACION_NO_ENCONTRADA", "No se encontró la obligación indicada."));
        LocalDateTime programada = obligacion.getFechaVencimiento().minusDays(dias).atTime(HORA_ENVIO);
        boolean desactivado = obligacion.getCodigoContribuyente() != null
                && preferencias.findById(obligacion.getCodigoContribuyente())
                        .map(preferencia -> !preferencia.isRecordatorioVencimiento()).orElse(false);
        String estado = obligacion.saldo().signum() <= 0 ? "NO_REQUERIDO"
                : desactivado ? "DESACTIVADO"
                : programada.isAfter(LocalDateTime.now(reloj)) ? "PROGRAMADO" : "NO_ENVIADO";
        return new EstadoRecordatorio(null, id, estado, null, programada, null, dias);
    }

    private LocalDateTime ultimoCambio() {
        return jdbc.query("""
                SELECT max(fecha_hora) FROM seguridad.registro_auditoria WHERE accion = 'CAMBIAR_DIAS_RECORDATORIO'
                """, rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null);
    }

    private String ultimoResponsable() {
        return jdbc.query("""
                SELECT u.nombre_completo FROM seguridad.registro_auditoria a
                JOIN seguridad.usuario u ON u.id_usuario = a.id_usuario
                WHERE a.accion = 'CAMBIAR_DIAS_RECORDATORIO' ORDER BY a.fecha_hora DESC LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null);
    }

    private String nombreDe(String usuario) {
        return jdbc.query("SELECT nombre_completo FROM seguridad.usuario WHERE lower(nombre_usuario) = lower(?)",
                rs -> rs.next() ? rs.getString(1) : usuario, usuario);
    }

    private int anticipacion() {
        String valor = parametros.findById(ParametroConfiguracion.DIAS_RECORDATORIO)
                .map(ParametroConfiguracion::getValor).orElse("5");
        try {
            int dias = Integer.parseInt(valor);
            if (dias >= 1 && dias <= 10) return dias;
        } catch (NumberFormatException ignored) { }
        throw new ReglaNegocioException("ANTICIPACION_INVALIDA", "DIAS_RECORDATORIO debe estar entre 1 y 10.");
    }
}
