package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/* HU016 (RF-022): preferencias de aviso del contribuyente autenticado en el portal (pantalla P12). */
@Service
public class PreferenciaAvisoService {

    public record PreferenciaAvisoRespuesta(String codigoContribuyente, String nombreRazonSocial,
            TipoDocumento tipoDocumento, String numeroDocumentoEnmascarado, String correo,
            boolean recordatorioVencimiento, int diasAnticipacion, LocalDateTime actualizadoEn) {
    }

    private final SamnetGateway samnet;
    private final PreferenciaAvisoRepository preferencias;
    private final RecordatorioVencimientoService recordatorios;
    private final Clock reloj;

    public PreferenciaAvisoService(SamnetGateway samnet, PreferenciaAvisoRepository preferencias,
            RecordatorioVencimientoService recordatorios, Clock reloj) {
        this.samnet = samnet;
        this.preferencias = preferencias;
        this.recordatorios = recordatorios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PreferenciaAvisoRespuesta obtener(String codigoContribuyente) {
        Contribuyente contribuyente = contribuyente(codigoContribuyente);
        PreferenciaAviso preferencia = preferencias.findById(codigoContribuyente).orElse(null);
        boolean activo = (preferencia == null || preferencia.isRecordatorioVencimiento())
                && !sinCorreo(contribuyente);
        return respuesta(contribuyente, activo, preferencia == null ? null : preferencia.getActualizadoEn());
    }

    @Transactional
    public PreferenciaAvisoRespuesta actualizar(String codigoContribuyente, boolean recordatorioVencimiento) {
        Contribuyente contribuyente = contribuyente(codigoContribuyente);
        if (recordatorioVencimiento && sinCorreo(contribuyente)) {
            throw new DatoInvalidoException("CORREO_NO_REGISTRADO",
                    "No tienes un correo registrado; no es posible activar el recordatorio.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        PreferenciaAviso preferencia = preferencias.findById(codigoContribuyente)
                .orElseGet(() -> new PreferenciaAviso(codigoContribuyente, true, ahora));
        preferencia.cambiarRecordatorio(recordatorioVencimiento, ahora);
        preferencias.saveAndFlush(preferencia);
        return respuesta(contribuyente, recordatorioVencimiento, ahora);
    }

    private PreferenciaAvisoRespuesta respuesta(Contribuyente contribuyente, boolean activo,
            LocalDateTime actualizadoEn) {
        return new PreferenciaAvisoRespuesta(contribuyente.getCodigoContribuyente(),
                contribuyente.getNombreRazonSocial(), contribuyente.getTipoDocumento(),
                enmascarar(contribuyente.getNumeroDocumento()), texto(contribuyente.getCorreo()), activo,
                recordatorios.configuracion().diasAnticipacion(), actualizadoEn);
    }

    private Contribuyente contribuyente(String codigoContribuyente) {
        return samnet.buscarContribuyente(codigoContribuyente).stream()
                .filter(c -> codigoContribuyente.equalsIgnoreCase(c.getCodigoContribuyente()))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No se encontró el contribuyente de la sesión."));
    }

    /* El contribuyente solo ve los últimos cuatro dígitos de su documento. */
    private static String enmascarar(String documento) {
        String valor = texto(documento);
        if (valor == null) return null;
        if (valor.length() <= 4) return "*".repeat(valor.length());
        return "*".repeat(valor.length() - 4) + valor.substring(valor.length() - 4);
    }

    private static boolean sinCorreo(Contribuyente contribuyente) {
        return texto(contribuyente.getCorreo()) == null;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
