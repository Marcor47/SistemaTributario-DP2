package pe.municipalidad.tributario.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "contribuyente")
public class Contribuyente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_contribuyente", nullable = false, unique = true, length = 20)
    private String codigoContribuyente;

    @Column(name = "tipo_documento", nullable = false, length = 10)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, unique = true, length = 20)
    private String numeroDocumento;

    @Column(name = "nombres_razon_social", nullable = false, length = 200)
    private String nombresRazonSocial;

    @Column(name = "direccion_fiscal", nullable = false, length = 255)
    private String direccionFiscal;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Contribuyente() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public void setCodigoContribuyente(String codigoContribuyente) {
        this.codigoContribuyente = codigoContribuyente;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getNombresRazonSocial() {
        return nombresRazonSocial;
    }

    public void setNombresRazonSocial(String nombresRazonSocial) {
        this.nombresRazonSocial = nombresRazonSocial;
    }

    public String getDireccionFiscal() {
        return direccionFiscal;
    }

    public void setDireccionFiscal(String direccionFiscal) {
        this.direccionFiscal = direccionFiscal;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
