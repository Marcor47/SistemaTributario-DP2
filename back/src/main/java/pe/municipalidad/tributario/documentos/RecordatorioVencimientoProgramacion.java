package pe.municipalidad.tributario.documentos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordatorioVencimientoProgramacion {
    private static final Logger LOG = LoggerFactory.getLogger(RecordatorioVencimientoProgramacion.class);
    private final RecordatorioVencimientoService servicio;
    public RecordatorioVencimientoProgramacion(RecordatorioVencimientoService servicio) { this.servicio = servicio; }

    @Scheduled(cron = "${tributario.recordatorios.programacion:0 0 8 * * *}", zone = "America/Lima")
    public void ejecutar() {
        try { servicio.procesar(); }
        catch (RuntimeException ex) { LOG.error("No se pudieron procesar los recordatorios", ex); }
    }
}
