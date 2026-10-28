package pe.municipalidad.tributario.analitica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Hecho analítico por cuota de cada tributo, cargado por el ETL (RF-009, RF-012, RF-024, RF-050).
 * LoteETL 1 carga *; * de 1 DimContribuyente; * de 1 DimPredio.
 */
@Entity
@Immutable
@Table(name = "hecho_cobranza", schema = "analitica")
public class HechoCobranza {

    @Id
    @Column(name = "id_hecho")
    private Long idHecho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lote", nullable = false)
    private LoteETL lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seudonimo_contribuyente", nullable = false)
    private DimContribuyente contribuyente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false)
    private DimPredio predio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", nullable = false, length = 25)
    private Tributo tributo;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "cuota", nullable = false)
    private int cuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_emitido", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoEmitido;

    @Column(name = "monto_insoluto", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoInsoluto;

    @Column(name = "monto_pagado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPagado;

    @Column(name = "monto_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoDescuento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 10)
    private EstadoObligacion estadoPago;

    protected HechoCobranza() {
    }

    public Long getIdHecho() {
        return idHecho;
    }

    public LoteETL getLote() {
        return lote;
    }

    public DimContribuyente getContribuyente() {
        return contribuyente;
    }

    public DimPredio getPredio() {
        return predio;
    }

    public Tributo getTributo() {
        return tributo;
    }

    public int getAnio() {
        return anio;
    }

    public int getCuota() {
        return cuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getMontoEmitido() {
        return montoEmitido;
    }

    public BigDecimal getMontoInsoluto() {
        return montoInsoluto;
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public BigDecimal getMontoDescuento() {
        return montoDescuento;
    }

    public EstadoObligacion getEstadoPago() {
        return estadoPago;
    }
}
