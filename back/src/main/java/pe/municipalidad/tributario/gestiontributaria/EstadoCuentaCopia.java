package pe.municipalidad.tributario.gestiontributaria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * Única persistencia del paquete Gestión tributaria: la última copia del estado de cuenta de cada
 * contribuyente, para mostrarla con su fecha de corte cuando SAMNET no responde (RNF-002).
 */
@Entity
@Table(name = "estado_cuenta_copia", schema = "operativo")
public class EstadoCuentaCopia {

    @Id
    @Column(name = "codigo_contribuyente", length = 10)
    private String codigoContribuyente;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @Column(name = "total_deuda", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeuda;

    @Column(name = "total_pagado", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPagado;

    /* Contribuyente y obligaciones en JSON, tal como los devolvió SAMNET. */
    @Column(name = "detalle", nullable = false, columnDefinition = "text")
    private String detalle;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected EstadoCuentaCopia() {
    }

    public EstadoCuentaCopia(String codigoContribuyente) {
        this.codigoContribuyente = codigoContribuyente;
    }

    public void actualizar(LocalDate fechaCorte, BigDecimal totalDeuda, BigDecimal totalPagado, String detalle,
            LocalDateTime fechaRegistro) {
        this.fechaCorte = fechaCorte;
        this.totalDeuda = totalDeuda;
        this.totalPagado = totalPagado;
        this.detalle = detalle;
        this.fechaRegistro = fechaRegistro;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public BigDecimal getTotalDeuda() {
        return totalDeuda;
    }

    public BigDecimal getTotalPagado() {
        return totalPagado;
    }

    public String getDetalle() {
        return detalle;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
