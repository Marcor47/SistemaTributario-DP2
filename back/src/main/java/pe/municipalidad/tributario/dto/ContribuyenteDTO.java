package pe.municipalidad.tributario.dto;

public class ContribuyenteDTO {
    private Long id;
    private String codigoContribuyente;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombresRazonSocial;
    private String direccionFiscal;
    private String telefono;
    private String email;

    public ContribuyenteDTO() {
    }

    public ContribuyenteDTO(Long id, String codigoContribuyente, String tipoDocumento,
                            String numeroDocumento, String nombresRazonSocial,
                            String direccionFiscal, String telefono, String email) {
        this.id = id;
        this.codigoContribuyente = codigoContribuyente;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombresRazonSocial = nombresRazonSocial;
        this.direccionFiscal = direccionFiscal;
        this.telefono = telefono;
        this.email = email;
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
}
