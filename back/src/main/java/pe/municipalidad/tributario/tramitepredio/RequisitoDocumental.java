package pe.municipalidad.tributario.tramitepredio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;

/* Documento que se pide según el tipo de trámite y de predio (RF-061). */
@Entity
@Table(name = "requisito_documental", schema = "operativo")
public class RequisitoDocumental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requisito")
    private Long idRequisito;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tramite", nullable = false, length = 10)
    private TipoTramitePredio tipoTramite;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_predio", nullable = false, length = 10)
    private TipoPredio tipoPredio;

    @Column(name = "nombre_documento", nullable = false, length = 150)
    private String nombreDocumento;

    /* La solicitud no se envía si falta un requisito obligatorio. */
    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    protected RequisitoDocumental() {
    }

    public RequisitoDocumental(TipoTramitePredio tipoTramite, TipoPredio tipoPredio, String nombreDocumento,
            boolean obligatorio) {
        this.tipoTramite = tipoTramite;
        this.tipoPredio = tipoPredio;
        this.nombreDocumento = nombreDocumento;
        this.obligatorio = obligatorio;
    }

    public Long getIdRequisito() {
        return idRequisito;
    }

    public TipoTramitePredio getTipoTramite() {
        return tipoTramite;
    }

    public TipoPredio getTipoPredio() {
        return tipoPredio;
    }

    public String getNombreDocumento() {
        return nombreDocumento;
    }

    public boolean isObligatorio() {
        return obligatorio;
    }
}
