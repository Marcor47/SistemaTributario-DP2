package pe.municipalidad.tributario.indicador;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/*
 * HU008: al iniciar, calcula el índice de morosidad de las cargas publicadas que aún no lo tienen. Las cargas del
 * perfil local se insertan por SQL sin pasar por el cálculo; así la pantalla muestra valores calculados de los
 * hechos y no valores escritos a mano. Solo se activa con tributario.indicadores.morosidad.calcular-al-iniciar.
 */
@Component
@ConditionalOnProperty(name = "tributario.indicadores.morosidad.calcular-al-iniciar", havingValue = "true")
public class CalculoMorosidadInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CalculoMorosidadInicial.class);

    private final IndiceMorosidadService indiceMorosidadService;

    public CalculoMorosidadInicial(IndiceMorosidadService indiceMorosidadService) {
        this.indiceMorosidadService = indiceMorosidadService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> calculadas = indiceMorosidadService.calcularCargasPendientes();
        if (!calculadas.isEmpty()) {
            LOG.info("Índice de morosidad calculado para las cargas {}", calculadas);
        }
    }
}
