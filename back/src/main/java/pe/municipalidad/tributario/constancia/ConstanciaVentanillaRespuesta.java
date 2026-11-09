package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * Resultado de la emisión de Constancia de No Adeudo en Ventanilla (HU042, RF-055).
 * Registra explícitamente el número asignado automáticamente y el trabajador emisor.
 */
public record ConstanciaVentanillaRespuesta(
        String numeroConstancia,
        Long idSolicitud,
        String documentoContribuyente,
        String nombreContribuyente,
        String codigoContribuyente,
        LocalDateTime fechaEmision,
        String prediosIncluidos,
        String rangoFechas,
        String trabajadorEmisor,
        String nombreTrabajadorEmisor,
        String canal,
        String estado,
        String comprobantePago,
        BigDecimal montoDerechoTramite,
        boolean esDePrueba) {

    public static ConstanciaVentanillaRespuesta desde(Constancia constancia, TransaccionPago ordenPago,
            String nombreContribuyente, String codigoContribuyente) {
        SolicitudConstancia sol = constancia.getSolicitud();
        String usuarioEmisor = constancia.getTrabajadorEmisor() != null
                ? constancia.getTrabajadorEmisor().getNombreUsuario() : null;
        String nombreEmisor = constancia.getTrabajadorEmisor() != null
                ? constancia.getTrabajadorEmisor().getNombreCompleto() : null;

        return new ConstanciaVentanillaRespuesta(
                constancia.getNumeroConstancia(),
                sol.getIdSolicitud(),
                sol.getDocumentoContribuyente(),
                nombreContribuyente,
                codigoContribuyente,
                constancia.getFechaEmision(),
                constancia.getPrediosIncluidos(),
                constancia.getRangoFechas(),
                usuarioEmisor,
                nombreEmisor,
                sol.getCanal().name(),
                sol.getEstado().name(),
                ordenPago != null ? ordenPago.getComprobante() : null,
                ordenPago != null ? ordenPago.getMontoTotal() : null,
                constancia.isEsDePrueba());
    }
}

