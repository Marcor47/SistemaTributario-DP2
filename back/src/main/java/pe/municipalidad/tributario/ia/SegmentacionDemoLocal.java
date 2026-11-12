package pe.municipalidad.tributario.ia;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/*
 * Solo perfil local (demostración): al iniciar calcula la segmentación K-means de la última carga y, si quedó en
 * borrador, la aprueba con el usuario de la jefatura de las semillas, por el mismo camino de HU031. Así el filtro
 * «Segmento» de la plataforma analítica, el dashboard y el mapa muestran los segmentos del modelo en cada máquina
 * de desarrollo. En producción no corre: la jefatura aprueba a mano (RF-043).
 * La misma carga con los mismos parámetros da el mismo modelo, así que en los reinicios no hace nada.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "tributario.segmentacion.publicar-al-iniciar", havingValue = "true")
public class SegmentacionDemoLocal implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SegmentacionDemoLocal.class);

    private final SegmentacionModeloService modelo;
    private final SegmentacionService segmentacion;
    private final String aprobador;

    public SegmentacionDemoLocal(SegmentacionModeloService modelo, SegmentacionService segmentacion,
            @Value("${tributario.segmentacion.aprobador-demo:gerencia}") String aprobador) {
        this.modelo = modelo;
        this.segmentacion = segmentacion;
        this.aprobador = aprobador;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            CalculoSegmentacionRespuesta calculo = modelo.calcular("inicio-local");
            List<Long> borradores = calculo.segmentos().stream()
                    .filter(s -> s.estado() == EstadoSegmento.BORRADOR).map(SegmentoRespuesta::idSegmento).toList();
            if (borradores.isEmpty()) {
                LOG.info("Local: la segmentación K-means del lote {} ya estaba revisada", calculo.idLote());
                return;
            }
            segmentacion.aprobar(borradores, aprobador);
            LOG.info("Local: segmentación K-means del lote {} publicada para la demostración (k = {}, aprobada como {})",
                    calculo.idLote(), calculo.k(), aprobador);
        } catch (RuntimeException ex) {
            // La demostración sigue sin ella: se puede calcular y aprobar por API.
            LOG.warn("Local: no se pudo publicar la segmentación K-means al iniciar", ex);
        }
    }
}
