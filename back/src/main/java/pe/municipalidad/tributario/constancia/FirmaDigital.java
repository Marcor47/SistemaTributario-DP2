package pe.municipalidad.tributario.constancia;

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
import java.time.LocalDateTime;

/*
 * Firma digital de la solicitud (contribuyente) o de la constancia (Municipalidad), verificada contra un
 * certificado vigente de una entidad acreditada ante la IOFE (RF-021, RF-037, RNF-007).
 */
@Entity
@Table(name = "firma_digital", schema = "operativo")
public class FirmaDigital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_firma")
    private Long idFirma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudConstancia solicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_firmante", nullable = false, length = 15)
    private TipoFirmante tipoFirmante;

    @Column(name = "entidad_certificadora", nullable = false, length = 120)
    private String entidadCertificadora;

    @Column(name = "fecha_firma", nullable = false)
    private LocalDateTime fechaFirma;

    @Column(name = "valida", nullable = false)
    private boolean valida;

    @Column(name = "hash_documento", nullable = false, length = 64)
    private String hashDocumento;

    protected FirmaDigital() {
    }

    /* valida es el resultado de verificar() con FirmadorGateway antes de crear la firma. */
    public FirmaDigital(TipoFirmante tipoFirmante, String entidadCertificadora, LocalDateTime fechaFirma,
            boolean valida, String hashDocumento) {
        this.tipoFirmante = tipoFirmante;
        this.entidadCertificadora = entidadCertificadora;
        this.fechaFirma = fechaFirma;
        this.valida = valida;
        this.hashDocumento = hashDocumento;
    }

    void asignarA(SolicitudConstancia solicitud) {
        this.solicitud = solicitud;
    }

    public Long getIdFirma() {
        return idFirma;
    }

    public SolicitudConstancia getSolicitud() {
        return solicitud;
    }

    public TipoFirmante getTipoFirmante() {
        return tipoFirmante;
    }

    public String getEntidadCertificadora() {
        return entidadCertificadora;
    }

    public LocalDateTime getFechaFirma() {
        return fechaFirma;
    }

    public boolean isValida() {
        return valida;
    }

    public String getHashDocumento() {
        return hashDocumento;
    }
}
