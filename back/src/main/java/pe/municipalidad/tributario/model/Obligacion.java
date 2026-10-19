package pe.municipalidad.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "obligacion")
public class Obligacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribuyente_id", nullable = false)
    private Contribuyente contribuyente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tributo_id", nullable = false)
    private Tributo tributo;

    @Column(name = "periodo", nullable = false)
    private Integer periodo;

    @Column(name = "cuota", nullable = false)
    private Integer cuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_emision", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoEmision;

    @Column(name = "monto_pagado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Obligacion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Contribuyente getContribuyente() {
        return contribuyente;
    }

    public void setContribuyente(Contribuyente contribuyente) {
        this.contribuyente = contribuyente;
    }

    public Tributo getTributo() {
        return tributo;
    }

    public void setTributo(Tributo tributo) {
        this.tributo = tributo;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
