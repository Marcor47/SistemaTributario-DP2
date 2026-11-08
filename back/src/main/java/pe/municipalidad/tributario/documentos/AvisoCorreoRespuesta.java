package pe.municipalidad.tributario.documentos;

import java.time.LocalDateTime;

/*
 * HU012 (RF-013): Representación de un aviso por correo para la Gerencia de Rentas
 * y el historial de notificaciones del contribuyente.
 */
public record AvisoCorreoRespuesta(
        Long idAviso,
        TipoAviso tipo,
        String documentoContribuyente,
        String correoDestino,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaEnvio,
        EstadoAviso estado,
        Long idLote,
        String idObligacion) {

    public static AvisoCorreoRespuesta desde(AvisoCorreo aviso) {
        return new AvisoCorreoRespuesta(
                aviso.getIdAviso(),
                aviso.getTipo(),
                aviso.getDocumentoContribuyente(),
                aviso.getCorreoDestino(),
                aviso.getFechaProgramada(),
                aviso.getFechaEnvio(),
                aviso.getEstado(),
                aviso.getLote() != null ? aviso.getLote().getIdLote() : null,
                aviso.getIdObligacion());
    }
}

