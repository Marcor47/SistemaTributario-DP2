package pe.municipalidad.tributario.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ResourceNotFoundException;
import pe.municipalidad.tributario.dto.ContribuyenteDTO;
import pe.municipalidad.tributario.dto.EstadoCuentaDTO;
import pe.municipalidad.tributario.dto.ObligacionDetalleDTO;
import pe.municipalidad.tributario.dto.ResumenDeudaDTO;
import pe.municipalidad.tributario.model.Contribuyente;
import pe.municipalidad.tributario.repository.ContribuyenteRepository;
import pe.municipalidad.tributario.repository.ObligacionRepository;
import pe.municipalidad.tributario.repository.ObligacionViewProjection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EstadoCuentaService {

    private final ContribuyenteRepository contribuyenteRepository;
    private final ObligacionRepository obligacionRepository;

    public EstadoCuentaService(ContribuyenteRepository contribuyenteRepository,
                               ObligacionRepository obligacionRepository) {
        this.contribuyenteRepository = contribuyenteRepository;
        this.obligacionRepository = obligacionRepository;
    }

    public EstadoCuentaDTO obtenerEstadoCuenta(String identificador) {
        if (identificador == null || identificador.trim().isEmpty()) {
            throw new IllegalArgumentException("El identificador del contribuyente es obligatorio");
        }

        String idLimpio = identificador.trim();

        // 1. Validar existencia del contribuyente
        Contribuyente contribuyente = contribuyenteRepository.findByIdentificador(idLimpio)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró ningún contribuyente con el identificador: " + idLimpio));

        ContribuyenteDTO contribuyenteDTO = new ContribuyenteDTO(
                contribuyente.getId(),
                contribuyente.getCodigoContribuyente(),
                contribuyente.getTipoDocumento(),
                contribuyente.getNumeroDocumento(),
                contribuyente.getNombresRazonSocial(),
                contribuyente.getDireccionFiscal(),
                contribuyente.getTelefono(),
                contribuyente.getEmail()
        );

        // 2. Invocar el Procedimiento Almacenado / Vista en PostgreSQL
        List<ObligacionViewProjection> resultados;
        try {
            resultados = obligacionRepository.consultarPorProcedimientoAlmacenado(idLimpio);
        } catch (Exception e) {
            // Fallback en caso de que el procedimiento no esté disponible o falle
            resultados = obligacionRepository.consultarPorVistaAnalitica(idLimpio);
        }

        List<ObligacionDetalleDTO> obligacionesDTO = new ArrayList<>();
        BigDecimal totalEmitido = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;
        BigDecimal totalPendiente = BigDecimal.ZERO;
        BigDecimal totalVencido = BigDecimal.ZERO;

        int vencidas = 0;
        int pendientes = 0;
        int pagadas = 0;

        for (ObligacionViewProjection p : resultados) {
            BigDecimal emision = p.getMontoEmision() != null ? p.getMontoEmision() : BigDecimal.ZERO;
            BigDecimal pagado = p.getMontoPagado() != null ? p.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal pendiente = p.getSaldoPendiente() != null ? p.getSaldoPendiente() : emision.subtract(pagado);
            BigDecimal vencido = p.getDeudaVencida() != null ? p.getDeudaVencida() : BigDecimal.ZERO;

            totalEmitido = totalEmitido.add(emision);
            totalPagado = totalPagado.add(pagado);
            totalPendiente = totalPendiente.add(pendiente);
            totalVencido = totalVencido.add(vencido);

            String estado = p.getEstado() != null ? p.getEstado().toUpperCase() : "PENDIENTE";
            switch (estado) {
                case "VENCIDO" -> vencidas++;
                case "PAGADO" -> pagadas++;
                default -> pendientes++;
            }

            ObligacionDetalleDTO item = new ObligacionDetalleDTO(
                    p.getObligacionId(),
                    p.getTributoId(),
                    p.getTributoCodigo(),
                    p.getTributoNombre(),
                    p.getTributoCategoria(),
                    p.getTributoColor(),
                    p.getPeriodo(),
                    p.getCuota(),
                    p.getFechaVencimiento(),
                    emision,
                    pagado,
                    pendiente,
                    vencido,
                    estado,
                    p.getFechaPago()
            );
            obligacionesDTO.add(item);
        }

        ResumenDeudaDTO resumenDTO = new ResumenDeudaDTO(
                totalEmitido,
                totalPagado,
                totalPendiente,
                totalVencido,
                obligacionesDTO.size(),
                vencidas,
                pendientes,
                pagadas
        );

        return new EstadoCuentaDTO(contribuyenteDTO, resumenDTO, obligacionesDTO);
    }
}
