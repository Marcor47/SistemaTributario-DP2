package pe.municipalidad.tributario.documentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import pe.municipalidad.tributario.integracion.samnet.DetalleRecordatorio;

/* HU016 (RF-022): página de recordatorios procesados en un día, con el detalle de la obligación. */
public record EnviosRecordatorioRespuesta(
        int total,
        int pagina,
        int tamanio,
        int totalPaginas,
        List<EnvioRecordatorioRespuesta> envios) {

    public static EnviosRecordatorioRespuesta de(Page<AvisoCorreo> pagina,
            Map<String, DetalleRecordatorio> detalles) {
        List<EnvioRecordatorioRespuesta> items = pagina.getContent().stream()
                .map(aviso -> EnvioRecordatorioRespuesta.desde(aviso, detalles.get(aviso.getIdObligacion())))
                .toList();
        return new EnviosRecordatorioRespuesta(Math.toIntExact(pagina.getTotalElements()), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalPages(), items);
    }

    public record EnvioRecordatorioRespuesta(
            Long idAviso,
            String idObligacion,
            String documentoContribuyente,
            String codigoContribuyente,
            String nombre,
            String correoDestino,
            String tributo,
            LocalDate fechaVencimiento,
            BigDecimal saldo,
            EstadoAviso estado,
            LocalDateTime fechaProgramada,
            LocalDateTime fechaEnvio) {

        static EnvioRecordatorioRespuesta desde(AvisoCorreo aviso, DetalleRecordatorio detalle) {
            return new EnvioRecordatorioRespuesta(
                    aviso.getIdAviso(),
                    aviso.getIdObligacion(),
                    aviso.getDocumentoContribuyente(),
                    detalle == null ? null : detalle.codigoContribuyente(),
                    detalle == null ? null : detalle.nombre(),
                    aviso.getCorreoDestino(),
                    detalle == null ? null : detalle.tributo(),
                    detalle == null ? null : detalle.fechaVencimiento(),
                    detalle == null ? null : detalle.saldo(),
                    aviso.getEstado(),
                    aviso.getFechaProgramada(),
                    aviso.getFechaEnvio());
        }
    }
}
