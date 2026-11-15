package pe.municipalidad.tributario.formularios;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.documentos.PlantillasInstitucionales;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;

/*
 * Al iniciar, publica con el formato institucional los formularios oficiales cuya versión vigente todavía es un
 * archivo de relleno (los que se cargaron sin diseño). Crea una versión nueva, así el historial se conserva; las
 * versiones que la Gerencia publique desde la pantalla no se tocan. Si ya están al día, no hace nada.
 */
@Component
public class PlantillasInstitucionalesIniciales implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlantillasInstitucionalesIniciales.class);
    /* Un archivo de relleno pesa unos cientos de bytes; uno institucional, con el logo, decenas de KB. */
    private static final int TAMANO_RELLENO = 4096;
    private static final List<String> CODIGOS = List.of("PL-001", "FO-001", "FO-002", "FO-003");

    private final FormularioService formularios;
    private final VersionPlantillaRepository versiones;
    private final PlantillasInstitucionales plantillas;

    public PlantillasInstitucionalesIniciales(FormularioService formularios, VersionPlantillaRepository versiones,
            PlantillasInstitucionales plantillas) {
        this.formularios = formularios;
        this.versiones = versiones;
        this.plantillas = plantillas;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        for (String codigo : CODIGOS) {
            var formato = plantillas.formato(codigo).orElseThrow();
            versiones.findByPlantillaCodigoAndVigenteTrue(codigo)
                    .filter(vigente -> vigente.descargar().length < TAMANO_RELLENO)
                    .ifPresent(vigente -> {
                        try {
                            var publicado = formularios.publicar(codigo, formato.nombre(), null,
                                    formato.archivo(vigente.getNumeroVersion() + 1), plantillas.generar(formato));
                            LOG.info("Formulario {} publicado con el formato institucional (versión {})", codigo,
                                    publicado.version());
                        } catch (RuntimeException ex) {
                            LOG.warn("No se pudo publicar el formulario {} con el formato institucional: {}", codigo,
                                    ex.getMessage());
                        }
                    });
        }
    }
}
