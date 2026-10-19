package pe.municipalidad.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ObligacionDetalleDTO {
    private Long id;
    private Long tributoId;
    private String tributoCodigo;
    private String tributoNombre;
    private String tributoCategoria;
    private String tributoColor;
    private Integer periodo;
    private Integer cuota;
    private LocalDate fechaVencimiento;
    private BigDecimal montoEmision;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private BigDecimal deudaVencida;
    private String estado;
    private LocalDate fechaPago;

    public ObligacionDetalleDTO() {
    }

    public ObligacionDetalleDTO(Long id, Long tributoId, String tributoCodigo, String tributoNombre,
                                String tributoCategoria, String tributoColor, Integer periodo,
                                Integer cuota, LocalDate fechaVencimiento, BigDecimal montoEmision,
                                BigDecimal montoPagado, BigDecimal saldoPendiente,
                                BigDecimal deudaVencida, String estado, LocalDate fechaPago) {
        this.id = id;
        this.tributoId = tributoId;
        this.tributoCodigo = tributoCodigo;
        this.tributoNombre = tributoNombre;
        this.tributoCategoria = tributoCategoria;
        this.tributoColor = tributoColor;
        this.periodo = periodo;
        this.cuota = cuota;
        this.fechaVencimiento = fechaVencimiento;
        this.montoEmision = montoEmision;
        this.montoPagado = montoPagado;
        this.saldoPendiente = saldoPendiente;
        this.deudaVencida = deudaVencida;
        this.estado = estado;
        this.fechaPago = fechaPago;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTributoId() {
        return tributoId;
    }

    public void setTributoId(Long tributoId) {
        this.tributoId = tributoId;
    }

    public String getTributoCodigo() {
        return tributoCodigo;
    }

    public void setTributoCodigo(String tributoCodigo) {
        this.tributoCodigo = tributoCodigo;
    }

    public String getTributoNombre() {
        return tributoNombre;
    }

    public void setTributoNombre(String tributoNombre) {
        this.tributoNombre = tributoNombre;
    }

    public String getTributoCategoria() {
        return tributoCategoria;
    }

    public void setTributoCategoria(String tributoCategoria) {
        this.tributoCategoria = tributoCategoria;
    }

    public String getTributoColor() {
        return tributoColor;
    }

    public void setTributoColor(String tributoColor) {
        this.tributoColor = tributoColor;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public void setPeriodo(Integer periodo) {
        this.periodo = periodo;
    }

    public Integer getCuota() {
        return cuota;
    }

    public void setCuota(Integer cuota) {
        this.cuota = cuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public BigDecimal getMontoEmision() {
        return montoEmision;
    }

    public void setMontoEmision(BigDecimal montoEmision) {
        this.montoEmision = montoEmision;
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public void setMontoPagado(BigDecimal montoPagado) {
        this.montoPagado = montoPagado;
    }

    public BigDecimal getSaldoPendiente() {
        return saldoPendiente;
    }

    public void setSaldoPendiente(BigDecimal saldoPendiente) {
        this.saldoPendiente = saldoPendiente;
    }

    public BigDecimal getDeudaVencida() {
        return deudaVencida;
    }

    public void setDeudaVencida(BigDecimal deudaVencida) {
        this.deudaVencida = deudaVencida;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }
}
