package pe.municipalidad.tributario.reporteria;

import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.documentos.DocumentoInstitucionalPdf;
import pe.municipalidad.tributario.ia.ResumenEjecutivo;
import pe.municipalidad.tributario.reporteria.ResumenEjecutivoService.IndicadorUsado;

/* PDF del resumen ejecutivo revisado, con la plantilla institucional y la tabla de indicadores que lo sustentan. */
@Component
public class GeneradorResumenEjecutivoPdf {

    public byte[] generar(ResumenEjecutivo resumen, List<IndicadorUsado> indicadores) {
        YearMonth mes = YearMonth.parse(resumen.getPeriodo());
        String codigo = "RE-" + resumen.getPeriodo().replace("-", "") + "-" + resumen.getIdResumen();
        String revisor = resumen.getRevisor() == null ? "Gerencia de Rentas" : resumen.getRevisor().getNombreCompleto();
        return new DocumentoInstitucionalPdf("Gerencia de Rentas", "Resumen ejecutivo de gestión tributaria", codigo,
                resumen.getFechaGeneracion())
                .datos(List.of(
                        new DocumentoInstitucionalPdf.Dato("Periodo:", capitalizar(ResumenEjecutivoService.nombreMes(mes))),
                        new DocumentoInstitucionalPdf.Dato("Fuente:", "Carga analítica publicada de la Gerencia de Rentas"),
                        new DocumentoInstitucionalPdf.Dato("Revisado por:", revisor)))
                .seccion("Resumen")
                .parrafo(resumen.getTexto())
                .seccion("Indicadores que sustentan el resumen")
                .tabla(List.of("Indicador", "Valor"),
                        indicadores.stream().map(i -> List.of(i.nombre(), ResumenEjecutivoService.cifra(i))).toList(),
                        new float[] {3.2f, 1.2f}, new boolean[] {false, true})
                .nota("Texto redactado con apoyo de un modelo de lenguaje a partir de los indicadores calculados y "
                        + "revisado por personal de la Gerencia de Rentas antes de su emisión.")
                .firma(revisor, "Gerencia de Rentas", null)
                .generar();
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
