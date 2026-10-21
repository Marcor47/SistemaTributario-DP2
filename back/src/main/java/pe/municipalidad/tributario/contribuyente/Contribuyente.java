package pe.municipalidad.tributario.contribuyente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* El código de contribuyente es la llave natural que asigna SAMNET; por eso no se usa un id generado. */
@Entity
@Table(name = "contribuyente", schema = "tributario")
public class Contribuyente {

    @Id
    @Column(name = "codigo", length = 10)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false, length = 10)
    private TipoPersona tipoPersona;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 5)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 12)
    private String numeroDocumento;

    @Column(name = "domicilio_fiscal", length = 250)
    private String domicilioFiscal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoContribuyente estado;

    protected Contribuyente() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getDomicilioFiscal() {
        return domicilioFiscal;
    }

    public EstadoContribuyente getEstado() {
        return estado;
    }
}
