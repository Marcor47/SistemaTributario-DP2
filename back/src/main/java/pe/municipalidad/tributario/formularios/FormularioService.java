package pe.municipalidad.tributario.formularios;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.municipalidad.tributario.documentos.PlantillaDocumento;
import pe.municipalidad.tributario.documentos.PlantillaDocumentoRepository;
import pe.municipalidad.tributario.documentos.TipoPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantilla;
import pe.municipalidad.tributario.documentos.VersionPlantillaRepository;

/* HU049: los formularios son plantillas del maestro (operativo.plantilla_documento y version_plantilla). */
@Service
@Profile("demo")
public class FormularioService {
    public record Resumen(String codigo, String nombre, int version, java.time.Instant fechaPublicacion) {
        static Resumen from(VersionPlantilla v) {
            return new Resumen(v.getPlantilla().getCodigo(), v.getPlantilla().getNombre(), v.getNumeroVersion(),
                    v.getFechaPublicacion());
        }
    }

    private final PlantillaDocumentoRepository plantillas;
    private final VersionPlantillaRepository versiones;

    public FormularioService(PlantillaDocumentoRepository plantillas, VersionPlantillaRepository versiones) {
        this.plantillas = plantillas;
        this.versiones = versiones;
    }

    @Transactional(readOnly = true)
    public List<Resumen> vigentes() {
        return versiones.findByVigenteTrueOrderByPlantillaCodigoAsc().stream().map(Resumen::from).toList();
    }

    @Transactional(readOnly = true)
    public VersionPlantilla vigente(String codigo) {
        return versiones.findByPlantillaCodigoAndVigenteTrue(codigo).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulario no encontrado"));
    }

    @Transactional
    public synchronized Resumen publicar(String codigo, String nombre, String archivo, byte[] contenido) {
        if (codigo == null || !codigo.matches("[A-Z0-9_-]{2,80}") || nombre == null
                || nombre.isBlank() || nombre.length() > 200 || archivo == null || contenido == null
                || contenido.length == 0 || contenido.length > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formulario inválido");
        }
        String extension;
        String contentType;
        if (archivo.toLowerCase().endsWith(".pdf") && startsWith(contenido, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            extension = ".pdf";
            contentType = "application/pdf";
        } else if (archivo.toLowerCase().endsWith(".docx") && isDocx(contenido)) {
            extension = ".docx";
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan PDF o DOCX válidos");
        }
        PlantillaDocumento plantilla = plantillas.findByCodigo(codigo).orElseGet(() -> plantillas.save(
                new PlantillaDocumento(codigo, nombre.trim(), TipoPlantilla.FORMULARIO_MANUAL, null)));
        plantilla.renombrar(nombre.trim());
        versiones.findByPlantillaCodigoAndVigenteTrue(codigo).ifPresent(anterior -> {
            anterior.retirar();
            /* Se escribe antes de insertar la nueva: solo puede haber una versión vigente por plantilla. */
            versiones.saveAndFlush(anterior);
        });
        int nuevaVersion = versiones.findTopByPlantillaCodigoOrderByNumeroVersionDesc(codigo)
                .map(v -> v.getNumeroVersion() + 1).orElse(1);
        VersionPlantilla nueva = new VersionPlantilla(plantilla, nuevaVersion, codigo + "-v" + nuevaVersion + extension,
                contentType, contenido, null, Instant.now());
        return Resumen.from(versiones.save(nueva));
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private boolean isDocx(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            boolean contentTypes = false;
            boolean document = false;
            while ((entry = zip.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) contentTypes = true;
                if ("word/document.xml".equals(entry.getName())) document = true;
            }
            return contentTypes && document;
        } catch (IOException ignored) {
            return false;
        }
    }
}
