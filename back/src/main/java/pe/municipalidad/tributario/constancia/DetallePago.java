package pe.municipalidad.tributario.constancia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Cotización por obligación, con la fuente municipal del descuento. */
@Embeddable
public class DetallePago {
    @Column(name = "id_obligacion", nullable = false)
    private Long idObligacion;

    @Column(name = "monto_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "monto_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoDescuento;

    @Column(name = "id_beneficio", length = 20)
    private String idBeneficio;

    /* Interés moratorio de la cuota vencida a la fecha de la orden (Código Tributario, art. 33). */
    @Column(name = "monto_interes", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoInteres = BigDecimal.ZERO;

    protected DetallePago() {
    }

    public DetallePago(Long idObligacion, BigDecimal montoBase, BigDecimal montoDescuento, String idBeneficio) {
        this(idObligacion, montoBase, montoDescuento, BigDecimal.ZERO, idBeneficio);
    }

    public DetallePago(Long idObligacion, BigDecimal montoBase, BigDecimal montoDescuento, BigDecimal montoInteres,
            String idBeneficio) {
        this.idObligacion = idObligacion;
        this.montoBase = montoBase;
        this.montoDescuento = montoDescuento;
        this.montoInteres = montoInteres;
        this.idBeneficio = idBeneficio;
    }

    public Long getIdObligacion() { return idObligacion; }
    public BigDecimal getMontoBase() { return montoBase; }
    public BigDecimal getMontoDescuento() { return montoDescuento; }
    public String getIdBeneficio() { return idBeneficio; }
    public BigDecimal getMontoInteres() { return montoInteres == null ? BigDecimal.ZERO : montoInteres; }
}
