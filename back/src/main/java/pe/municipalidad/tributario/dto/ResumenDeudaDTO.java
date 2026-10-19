package pe.municipalidad.tributario.dto;

import java.math.BigDecimal;

public class ResumenDeudaDTO {
    private BigDecimal totalEmitido;
    private BigDecimal totalPagado;
    private BigDecimal totalPendiente;
    private BigDecimal totalVencido;
    private int totalObligaciones;
    private int obligacionesVencidas;
    private int obligacionesPendientes;
    private int obligacionesPagadas;

    public ResumenDeudaDTO() {
        this.totalEmitido = BigDecimal.ZERO;
        this.totalPagado = BigDecimal.ZERO;
        this.totalPendiente = BigDecimal.ZERO;
        this.totalVencido = BigDecimal.ZERO;
    }

    public ResumenDeudaDTO(BigDecimal totalEmitido, BigDecimal totalPagado, BigDecimal totalPendiente,
                           BigDecimal totalVencido, int totalObligaciones, int obligacionesVencidas,
                           int obligacionesPendientes, int obligacionesPagadas) {
        this.totalEmitido = totalEmitido;
        this.totalPagado = totalPagado;
        this.totalPendiente = totalPendiente;
        this.totalVencido = totalVencido;
        this.totalObligaciones = totalObligaciones;
        this.obligacionesVencidas = obligacionesVencidas;
        this.obligacionesPendientes = obligacionesPendientes;
        this.obligacionesPagadas = obligacionesPagadas;
    }

    public BigDecimal getTotalEmitido() {
        return totalEmitido;
    }

    public void setTotalEmitido(BigDecimal totalEmitido) {
        this.totalEmitido = totalEmitido;
    }

    public BigDecimal getTotalPagado() {
        return totalPagado;
    }

    public void setTotalPagado(BigDecimal totalPagado) {
        this.totalPagado = totalPagado;
    }

    public BigDecimal getTotalPendiente() {
        return totalPendiente;
    }

    public void setTotalPendiente(BigDecimal totalPendiente) {
        this.totalPendiente = totalPendiente;
    }

    public BigDecimal getTotalVencido() {
        return totalVencido;
    }

    public void setTotalVencido(BigDecimal totalVencido) {
        this.totalVencido = totalVencido;
    }

    public int getTotalObligaciones() {
        return totalObligaciones;
    }

    public void setTotalObligaciones(int totalObligaciones) {
        this.totalObligaciones = totalObligaciones;
    }

    public int getObligacionesVencidas() {
        return obligacionesVencidas;
    }

    public void setObligacionesVencidas(int obligacionesVencidas) {
        this.obligacionesVencidas = obligacionesVencidas;
    }

    public int getObligacionesPendientes() {
        return obligacionesPendientes;
    }

    public void setObligacionesPendientes(int obligacionesPendientes) {
        this.obligacionesPendientes = obligacionesPendientes;
    }

    public int getObligacionesPagadas() {
        return obligacionesPagadas;
    }

    public void setObligacionesPagadas(int obligacionesPagadas) {
        this.obligacionesPagadas = obligacionesPagadas;
    }
}
