package pe.municipalidad.tributario.documentos;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.integracion.samnet.DetalleRecordatorio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * HU016 (RF-022): detalle paginado de los recordatorios procesados en un día. Incluye enviados, registrados
 * (modo local) y fallidos, y consulta a SAMNET el nombre, tributo, vencimiento y saldo de cada obligación.
 */
@Service
@Transactional(readOnly = true)
public class RecordatorioEnvioService {

    private static final int TAMANIO_MAXIMO = 100;

    private final AvisoCorreoRepository avisos;
    private final SamnetGateway samnet;
    private final Clock reloj;

    public RecordatorioEnvioService(AvisoCorreoRepository avisos, SamnetGateway samnet, Clock reloj) {
        this.avisos = avisos;
        this.samnet = samnet;
        this.reloj = reloj;
    }

    public EnviosRecordatorioRespuesta listar(LocalDate fecha, int pagina, int tamanio) {
        LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
        int paginaSegura = Math.max(0, pagina);
        int tamanioSeguro = Math.min(Math.max(1, tamanio), TAMANIO_MAXIMO);
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

        Page<AvisoCorreo> resultado = avisos.findByTipoAndFechaProgramadaBetween(
                TipoAviso.RECORDATORIO_VENCIMIENTO, desde, hasta,
                PageRequest.of(paginaSegura, tamanioSeguro,
                        Sort.by(Sort.Order.desc("fechaProgramada"), Sort.Order.desc("idAviso"))));
        List<String> idsObligacion = resultado.getContent().stream()
                .map(AvisoCorreo::getIdObligacion)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, DetalleRecordatorio> detalles = samnet.obtenerDetallesRecordatorio(idsObligacion);
        return EnviosRecordatorioRespuesta.de(resultado, detalles);
    }
}
