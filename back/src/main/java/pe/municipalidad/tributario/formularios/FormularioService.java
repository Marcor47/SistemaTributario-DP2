package pe.municipalidad.tributario.formularios;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("local")
public class FormularioService {
    public record Resumen(String codigo, String nombre, int version, java.time.Instant fechaPublicacion) {
        static Resumen from(FormularioVersion f) {
            return new Resumen(f.getCodigo(), f.getNombre(), f.getVersionNumero(), f.getFechaPublicacion());
        }
    }

    private final FormularioRepository repository;

    public FormularioService(FormularioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Resumen> vigentes() {
        return repository.findByVigenteTrueOrderByCodigoAsc().stream().map(Resumen::from).toList();
    }

    @Transactional(readOnly = true)
    public FormularioVersion vigente(String codigo) {
        return repository.findByCodigoAndVigenteTrue(codigo).orElseThrow(
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
        FormularioVersion anterior = repository.findByCodigoAndVigenteTrue(codigo).orElse(null);
        int nuevaVersion = repository.findTopByCodigoOrderByVersionNumeroDesc(codigo)
                .map(f -> f.getVersionNumero() + 1).orElse(1);
        if (anterior != null) {
            anterior.setVigente(false);
            repository.save(anterior);
        }
        FormularioVersion nuevo = new FormularioVersion(codigo, nombre.trim(), nuevaVersion,
                codigo + "-v" + nuevaVersion + extension, contentType, contenido);
        return Resumen.from(repository.save(nuevo));
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
