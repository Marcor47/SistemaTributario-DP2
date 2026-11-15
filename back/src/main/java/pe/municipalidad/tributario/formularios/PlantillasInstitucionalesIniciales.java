package pe.municipalidad.tributario.formularios;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.documentos.PlantillasInstitucionales;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;

/*
 * Al iniciar, publica con el formato institucional los formularios oficiales cuya versión vigente todavía es un
 * archivo de relleno o un borrador de prueba (los Word que se cargaron sin diseño, con el rótulo "DOCUMENTO DE
 * PRUEBA" / "DEMOSTRACIÓN"). Crea una versión nueva, así el historial se conserva; las versiones que la Gerencia
 * publique desde la pantalla no se tocan. Si ya están al día, no hace nada.
 */
@Component
public class PlantillasInstitucionalesIniciales implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlantillasInstitucionalesIniciales.class);
    /* Un archivo de relleno pesa unos cientos de bytes; uno institucional, con el logo, decenas de KB. */
    private static final int TAMANO_RELLENO = 4096;
    private static final List<String> CODIGOS = List.of("PL-001", "FO-001", "FO-002", "FO-003");
    /* Rótulos de los borradores de prueba que se sembraron antes de tener el formato institucional. */
    private static final List<String> MARCAS_BORRADOR = List.of("DOCUMENTO DE PRUEBA", "DEMOSTRACIÓN",
            "Formulario ficticio");

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
                    .filter(vigente -> sinFormatoInstitucional(vigente.descargar()))
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

    /* Relleno de pocos bytes o Word de prueba: ninguno lleva el encabezado ni el pie institucional. */
    static boolean sinFormatoInstitucional(byte[] contenido) {
        if (contenido == null || contenido.length < TAMANO_RELLENO) {
            return true;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8)) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                String nombre = entrada.getName();
                if (nombre.equals("word/document.xml") || nombre.startsWith("word/header")) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    if (MARCAS_BORRADOR.stream().anyMatch(xml::contains)) {
                        return true;
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            /* No es un Word (por ejemplo, un PDF publicado por la Gerencia): se respeta tal cual. */
            return false;
        }
        return false;
    }
}
