package pe.municipalidad.tributario.reporteria;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.reporte.ExportacionReporte;
import pe.municipalidad.tributario.reporte.FormatoExportacion;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;

/* Genera el archivo de la vista en pantalla y deja la exportación en la auditoría (RF-008, RF-060). */
@Service
public class ExportacionReporteriaService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String TIPO_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TIPO_PDF = "application/pdf";

    private final ReporteriaExportador exportador;
    private final UsuarioRepository usuarios;
    private final RegistroAuditoriaRepository auditorias;
    private final Clock reloj;

    public ExportacionReporteriaService(ReporteriaExportador exportador, UsuarioRepository usuarios,
            RegistroAuditoriaRepository auditorias, Clock reloj) {
        this.exportador = exportador;
        this.usuarios = usuarios;
        this.auditorias = auditorias;
        this.reloj = reloj;
    }

    @Transactional
    public ExportacionReporte exportar(ExportarVistaPeticion peticion, FormatoExportacion formato, String nombreUsuario) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String fecha = ahora.format(FORMATO_FECHA_HORA);
        String base = "plataforma-analitica-" + peticion.pestana() + "-" + LocalDate.now(reloj);
        ExportacionReporte exportacion = switch (formato) {
            case EXCEL -> new ExportacionReporte(exportador.excel(peticion, nombreUsuario, fecha), base + ".xlsx", TIPO_EXCEL);
            case PDF -> new ExportacionReporte(exportador.pdf(peticion, nombreUsuario, fecha), base + ".pdf", TIPO_PDF);
            default -> throw new DatoInvalidoException("FORMATO_INVALIDO", "Elige Excel o PDF.");
        };
        auditorias.save(new RegistroAuditoria(usuarios.findByNombreUsuario(nombreUsuario).orElse(null), "EXPORTAR",
                "PlataformaAnalitica", null, peticion.pestana() + ":" + formato + ":" + peticion.filtros(), ahora));
        return exportacion;
    }
}
