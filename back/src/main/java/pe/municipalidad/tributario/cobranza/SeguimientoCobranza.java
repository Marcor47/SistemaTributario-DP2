package pe.municipalidad.tributario.cobranza;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * Cada fila es el ingreso de una obligación a una etapa de cobranza. La etapa
 * actual es la de fecha de inicio más reciente. Se guarda solo el id de la
 * obligación porque las consultas buscan por ese id y no necesitan navegar la relación.
 */
@Entity
@Table(name = "seguimiento_cobranza", schema = "tributario")
public class SeguimientoCobranza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obligacion_id", nullable = false)
    private Long obligacionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false, length = 20)
    private EtapaCobranza etapa;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "documento_referencia", length = 40)
    private String documentoReferencia;

    @Column(name = "observacion", length = 250)
    private String observacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected SeguimientoCobranza() {
    }

    public Long getId() {
        return id;
    }

    public Long getObligacionId() {
        return obligacionId;
    }

    public EtapaCobranza getEtapa() {
        return etapa;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public String getDocumentoReferencia() {
        return documentoReferencia;
    }

    public String getObservacion() {
        return observacion;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
