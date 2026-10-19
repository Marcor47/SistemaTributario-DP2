package pe.municipalidad.tributario.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ObligacionViewProjection {
    Long getContribuyenteId();
    String getCodigoContribuyente();
    String getTipoDocumento();
    String getNumeroDocumento();
    String getNombresRazonSocial();
    String getDireccionFiscal();
    Long getObligacionId();
    Long getTributoId();
    String getTributoCodigo();
    String getTributoNombre();
    String getTributoCategoria();
    String getTributoColor();
    Integer getPeriodo();
    Integer getCuota();
    LocalDate getFechaVencimiento();
    BigDecimal getMontoEmision();
    BigDecimal getMontoPagado();
    BigDecimal getSaldoPendiente();
    BigDecimal getDeudaVencida();
    String getEstado();
    LocalDate getFechaPago();
}
