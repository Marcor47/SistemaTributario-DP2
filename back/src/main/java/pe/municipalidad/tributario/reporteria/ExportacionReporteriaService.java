package pe.municipalidad.tributario.reporteria;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.AccesoDenegadoException;
import pe.municipalidad.tributario.reporte.DimensionRestringida;
import pe.municipalidad.tributario.reporte.TipoReporte;
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
    private final AccesoReporteria acceso;
    private final ReporteriaService reporteria;
    private final CuboReporteria cubo;

    public ExportacionReporteriaService(ReporteriaExportador exportador, UsuarioRepository usuarios,
            RegistroAuditoriaRepository auditorias, Clock reloj, AccesoReporteria acceso,
            ReporteriaService reporteria, CuboReporteria cubo) {
        this.exportador = exportador;
        this.usuarios = usuarios;
        this.auditorias = auditorias;
        this.reloj = reloj;
        this.acceso = acceso;
        this.reporteria = reporteria;
        this.cubo = cubo;
    }

    @Transactional
    public ExportacionReporte exportar(ExportarVistaPeticion peticion, FormatoExportacion formato, String nombreUsuario) {
        peticion = vistaAutorizada(peticion);
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

    private ExportarVistaPeticion vistaAutorizada(ExportarVistaPeticion vista) {
        var reglas = acceso.actuales();
        if (reglas.isEmpty()) return vista;
        TipoReporte tipo = switch (vista.pestana()) {
            case "resumen" -> null;
            case "morosos" -> TipoReporte.MOROSOS;
            case "pricos" -> TipoReporte.PRICOS;
            case "buenos" -> TipoReporte.BUENOS_PAGADORES;
            default -> throw new DatoInvalidoException("PESTANA_INVALIDA", "Seleccione una pestaña válida.");
        };
        if (tipo != null && reglas.stream().anyMatch(r -> r.reporte() == tipo && r.dimension() == DimensionRestringida.REPORTE)) {
            throw new AccesoDenegadoException("REPORTE_RESTRINGIDO", "Su rol no puede exportar ese reporte.");
        }
        if (vista.consulta() == null) throw new DatoInvalidoException("CONSULTA_REQUERIDA",
                "Actualice la pantalla para exportar los datos autorizados.");
        var datos = reporteria.datos(vista.consulta().filtro(), null);
        var filas = datos.top().getOrDefault(vista.pestana(), List.of()).stream()
                .map(f -> List.<Object>of(f.codigo(), f.nombre(), f.emitido(), f.cobrado(), f.deuda())).toList();
        var ranking = new ExportarVistaPeticion.TablaExportada("Contribuyentes autorizados", "TABLA",
                List.of("Código", "Contribuyente", "Emitido", "Cobrado", "Deuda"), filas, null);
        // Los importes, filas e imágenes del navegador no son una fuente de datos autorizada.
        List<List<String>> indicadores = switch (vista.pestana()) {
            case "morosos" -> List.of(List.of("Morosos autorizados", String.valueOf(datos.totales().morosos())),
                    List.of("Deuda del alcance autorizado", monto(datos.totales().deuda())));
            case "pricos" -> List.of(List.of("PRICOS autorizados", String.valueOf(datos.pricos().cantidad())),
                    List.of("Emitido", monto(datos.pricos().emitido())),
                    List.of("Cobrado", monto(datos.pricos().cobrado())), List.of("Deuda", monto(datos.pricos().deuda())));
            case "buenos" -> List.of(List.of("Buenos pagadores autorizados", String.valueOf(datos.totales().buenosPagadores())),
                    List.of("Recaudado", monto(datos.totales().recaudadoBuenosPagadores())));
            default -> List.of(List.of("Contribuyentes autorizados", String.valueOf(datos.totales().contribuyentes())),
                    List.of("Emitido del alcance autorizado", monto(datos.totales().emitido())),
                    List.of("Cobrado del alcance autorizado", monto(datos.totales().cobrado())),
                    List.of("Deuda del alcance autorizado", monto(datos.totales().deuda())));
        };
        return new ExportarVistaPeticion(vista.pestana(), "Vista autorizada - " + vista.pestana(),
                cubo.carga().fechaCorte().toString(), vista.consulta().toString(), null, null, indicadores,
                List.of(), ranking, vista.consulta());
    }

    private static String monto(double valor) { return String.format(Locale.ROOT, "%.2f", valor); }
}
