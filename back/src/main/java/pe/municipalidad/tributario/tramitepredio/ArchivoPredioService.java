package pe.municipalidad.tributario.tramitepredio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.municipalidad.tributario.common.DatoInvalidoException;

/** Almacena los adjuntos cifrados fuera de los recursos públicos. La clave se configura fuera de Git. */
@Service
public class ArchivoPredioService {
    private static final int NONCE_BYTES = 12;
    private final Path carpeta;
    private final String claveBase64;
    private final SecureRandom aleatorio = new SecureRandom();

    public ArchivoPredioService(@Value("${tributario.archivos.predios:./storage/predios}") String carpeta,
            @Value("${tributario.archivos.clave:}") String claveBase64) {
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
        this.claveBase64 = claveBase64;
    }

    public ArchivoGuardado guardar(MultipartFile archivo) {
        if (archivo.isEmpty() || archivo.getSize() > DocumentoAdjunto.TAMANO_MAXIMO_BYTES) {
            throw new DatoInvalidoException("ARCHIVO_INVALIDO", "Cada archivo debe medir entre 1 byte y 10 MB.");
        }
        try {
            byte[] contenido = archivo.getBytes();
            TipoContenido tipo = detectarTipo(contenido);
            byte[] nonce = new byte[NONCE_BYTES];
            aleatorio.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(128, nonce));
            byte[] cifrado = cipher.doFinal(contenido);
            String nombre = UUID.randomUUID() + ".enc";
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve(nombre);
            Files.write(destino, ByteBuffer.allocate(nonce.length + cifrado.length)
                    .put(nonce).put(cifrado).array(), StandardOpenOption.CREATE_NEW);
            String recibido = archivo.getOriginalFilename() == null ? "archivo" :
                    archivo.getOriginalFilename().replace('\\', '/');
            String original = recibido.substring(recibido.lastIndexOf('/') + 1);
            if (original.isBlank()) original = "archivo";
            if (original.length() > 150) {
                original = original.substring(original.length() - 150);
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
            return new ArchivoGuardado(original, nombre, tipo, contenido.length, destino.toString(), hash);
        } catch (DatoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar el archivo del trámite.", ex);
        }
    }

    public byte[] leer(DocumentoAdjunto adjunto) {
        Path ruta = Path.of(adjunto.getRutaCifrada()).toAbsolutePath().normalize();
        if (!ruta.getParent().equals(carpeta)) {
            throw new IllegalStateException("Ruta de adjunto no válida.");
        }
        try {
            byte[] datos = Files.readAllBytes(ruta);
            if (datos.length <= NONCE_BYTES) {
                throw new IllegalStateException("Adjunto corrupto.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(128,
                    java.util.Arrays.copyOfRange(datos, 0, NONCE_BYTES)));
            return cipher.doFinal(datos, NONCE_BYTES, datos.length - NONCE_BYTES);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer el adjunto.", ex);
        }
    }

    public void eliminar(String ruta) {
        try {
            Path archivo = Path.of(ruta).toAbsolutePath().normalize();
            if (archivo.getParent().equals(carpeta)) {
                Files.deleteIfExists(archivo);
            }
        } catch (IOException ignored) {
            // El error original del trámite se conserva; un archivo huérfano se puede limpiar luego.
        }
    }

    private SecretKeySpec clave() {
        try {
            byte[] bytes = Base64.getDecoder().decode(claveBase64);
            if (bytes.length != 32) {
                throw new IllegalArgumentException("La clave de archivos debe contener 32 bytes.");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Configure PREDIO_FILES_KEY con 32 bytes aleatorios en Base64.", ex);
        }
    }

    private static TipoContenido detectarTipo(byte[] bytes) {
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') return TipoContenido.PDF;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return TipoContenido.JPG;
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && (bytes[4] & 0xff) == 13 && (bytes[5] & 0xff) == 10
                && (bytes[6] & 0xff) == 26 && (bytes[7] & 0xff) == 10) return TipoContenido.PNG;
        throw new DatoInvalidoException("TIPO_ARCHIVO_INVALIDO", "Solo se aceptan archivos PDF, JPG o PNG.");
    }

    public record ArchivoGuardado(String original, String nombre, TipoContenido tipo, long tamano,
            String ruta, String hash) {
    }
}
