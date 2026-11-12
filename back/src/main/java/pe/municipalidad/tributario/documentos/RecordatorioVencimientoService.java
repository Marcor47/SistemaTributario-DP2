package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracion;
import pe.municipalidad.tributario.seguridad.ParametroConfiguracionRepository;

/** HU016: recordatorios previos al vencimiento, con deduplicación por obligación. */
@Service
public class RecordatorioVencimientoService {
    public record Resultado(LocalDate fecha, int diasAnticipacion, int detectados, int enviados,
                            int sinCorreo, int duplicados, int fallidos, int simulados, boolean envioSimulado) {}

    private final SamnetGateway samnet;
    private final AvisoCorreoRepository avisos;
    private final CorreoGateway correo;
    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final ParametroConfiguracionRepository parametros;

    public RecordatorioVencimientoService(SamnetGateway samnet, AvisoCorreoRepository avisos, CorreoGateway correo,
            JdbcTemplate jdbc, Clock reloj, ParametroConfiguracionRepository parametros) {
        this.samnet = samnet;
        this.avisos = avisos;
        this.correo = correo;
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
        int enviados = 0, sinCorreo = 0, duplicados = 0, fallidos = 0, simulados = 0;
        for (Map<String, Object> fila : pendientes) {
            String id = String.valueOf(fila.get("id_obligacion"));
            String destino = texto(fila.get("correo"));
            String documento = texto(fila.get("numero_documento"));
            if (destino == null || documento == null) { sinCorreo++; continue; }
            if (avisos.existsByTipoAndIdObligacionAndEstado(TipoAviso.RECORDATORIO_VENCIMIENTO, id, EstadoAviso.ENVIADO)
                    || (correo.esSimulado() && avisos.existsByTipoAndIdObligacionAndEstado(
                            TipoAviso.RECORDATORIO_VENCIMIENTO, id, EstadoAviso.SIMULADO))) {
                duplicados++; continue;
            }
            AvisoCorreo aviso = new AvisoCorreo(TipoAviso.RECORDATORIO_VENCIMIENTO, documento, destino,
                    ahora, null, id);
            aviso.enviar(correo, "Recordatorio de vencimiento de tributos municipales",
                    "Su obligación N.° " + id + " vence el " + fila.get("fecha_vencimiento")
                            + ". Saldo pendiente: S/ " + fila.get("saldo") + ".", ahora);
            avisos.save(aviso);
            if (aviso.getEstado() == EstadoAviso.ENVIADO) enviados++;
            else if (aviso.getEstado() == EstadoAviso.SIMULADO) simulados++;
            else fallidos++;
        }
        return new Resultado(hoy, dias, pendientes.size(), enviados, sinCorreo, duplicados, fallidos, simulados,
                correo.esSimulado());
    }

    private static String texto(Object valor) {
        if (valor == null || valor.toString().isBlank()) return null;
        return valor.toString().trim();
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
