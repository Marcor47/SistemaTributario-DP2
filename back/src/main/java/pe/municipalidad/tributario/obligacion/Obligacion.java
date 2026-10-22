package pe.municipalidad.tributario.obligacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * Cuota emitida de un tributo para un periodo. El Impuesto Predial se emite al
 * contribuyente; los arbitrios se emiten por predio, por eso codigoPredio es opcional.
 */
@Entity
@Table(name = "obligacion", schema = "tributario")
public class Obligacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribuyente_codigo", nullable = false)
    private Contribuyente contribuyente;

    @Column(name = "codigo_predio", length = 12)
    private String codigoPredio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", nullable = false, length = 25)
    private Tributo tributo;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "numero_cuota", nullable = false)
    private int numeroCuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_emitido", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoEmitido;

    @Column(name = "monto_pagado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPagado;

    protected Obligacion() {
    }

    public BigDecimal saldo() {
        return montoEmitido.subtract(montoPagado);
    }

    public EstadoObligacion estadoAl(LocalDate fecha) {
        if (saldo().signum() <= 0) {
            return EstadoObligacion.PAGADA;
        }
        return fechaVencimiento.isBefore(fecha) ? EstadoObligacion.VENCIDA : EstadoObligacion.PENDIENTE;
    }

    /* Días transcurridos desde el vencimiento; 0 si aún no vence. */
    public long diasVencidaAl(LocalDate fecha) {
        return Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, fecha));
    }

    /* Mismo formato de periodo que usa el front: 2025-T3. */
    public String etiquetaPeriodo() {
        return anio + "-T" + numeroCuota;
    }

    public Long getId() {
        return id;
    }

    public Contribuyente getContribuyente() {
        return contribuyente;
    }

    public String getCodigoPredio() {
        return codigoPredio;
    }

    public Tributo getTributo() {
        return tributo;
    }

    public int getAnio() {
        return anio;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getMontoEmitido() {
        return montoEmitido;
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }
}
