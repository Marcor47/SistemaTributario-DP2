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
import pe.municipalidad.tributario.gestiontributaria.CanalPago;

/* Hecho analítico por pago: buenos pagadores y pagos en línea (RF-051, RF-062). Lo carga el ETL. */
@Entity
@Immutable
@Table(name = "hecho_pago", schema = "analitica")
public class HechoPago {

    @Id
    @Column(name = "id_hecho_pago")
    private Long idHechoPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lote", nullable = false)
    private LoteETL lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seudonimo_contribuyente", nullable = false)
    private DimContribuyente contribuyente;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalPago canal;

    @Column(name = "monto_pagado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPagado;

    @Column(name = "beneficio_aplicado", length = 20)
    private String beneficioAplicado;

    @Column(name = "monto_correspondiente", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoCorrespondiente;

    protected HechoPago() {
    }

    public Long getIdHechoPago() {
        return idHechoPago;
    }

    public LoteETL getLote() {
        return lote;
    }

    public DimContribuyente getContribuyente() {
        return contribuyente;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public CanalPago getCanal() {
        return canal;
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public String getBeneficioAplicado() {
        return beneficioAplicado;
    }

    public BigDecimal getMontoCorrespondiente() {
        return montoCorrespondiente;
    }
}
