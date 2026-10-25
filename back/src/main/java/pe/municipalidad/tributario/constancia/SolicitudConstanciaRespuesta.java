package pe.municipalidad.tributario.constancia;

import java.time.LocalDateTime;

public record SolicitudConstanciaRespuesta(
        Long idSolicitud,
        CanalSolicitud canal,
        EstadoConstancia estado,
        LocalDateTime fechaSolicitud,
        String motivoRechazo,
        FirmaRespuesta firmaContribuyente) {

    public static SolicitudConstanciaRespuesta desde(SolicitudConstancia solicitud) {
        FirmaRespuesta firma = solicitud.getFirmas().stream()
                .filter(f -> f.getTipoFirmante() == TipoFirmante.CONTRIBUYENTE)
                .findFirst()
                .map(FirmaRespuesta::desde)
                .orElse(null);
        return new SolicitudConstanciaRespuesta(
                solicitud.getIdSolicitud(),
                solicitud.getCanal(),
                solicitud.getEstado(),
                solicitud.getFechaSolicitud(),
                solicitud.getMotivoRechazo(),
                firma);
    }

    public record FirmaRespuesta(String entidadCertificadora, LocalDateTime fechaFirma, String hashDocumento) {

        static FirmaRespuesta desde(FirmaDigital firma) {
            return new FirmaRespuesta(firma.getEntidadCertificadora(), firma.getFechaFirma(),
                    firma.getHashDocumento());
        }
    }
}
