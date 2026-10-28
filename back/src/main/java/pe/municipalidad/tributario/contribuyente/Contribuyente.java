package pe.municipalidad.tributario.contribuyente;

/*
 * Persona natural o jurídica obligada al pago de tributos (RF-001, RF-048). No tiene persistencia propia:
 * se obtiene de SAMNET mediante SamnetGateway, que hoy consulta los SP de la BD sintética.
 */
public class Contribuyente {

    private final String codigoContribuyente;
    private final TipoDocumento tipoDocumento;
    private final String numeroDocumento;
    private final String nombreRazonSocial;
    private final String regimen;
    private final TipoPersona tipoPersona;
    private final String condicion;
    private final String correo;
    private final String telefonos;
    private final EstadoContribuyente estado;
    private final DomicilioFiscal domicilioFiscal;

    public Contribuyente(String codigoContribuyente, TipoDocumento tipoDocumento, String numeroDocumento,
            String nombreRazonSocial, String regimen, TipoPersona tipoPersona, String condicion, String correo,
            String telefonos, EstadoContribuyente estado, DomicilioFiscal domicilioFiscal) {
        this.codigoContribuyente = codigoContribuyente;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombreRazonSocial = nombreRazonSocial;
        this.regimen = regimen;
        this.tipoPersona = tipoPersona;
        this.condicion = condicion;
        this.correo = correo;
        this.telefonos = telefonos;
        this.estado = estado;
        this.domicilioFiscal = domicilioFiscal;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombreRazonSocial() {
        return nombreRazonSocial;
    }

    public String getRegimen() {
        return regimen;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public String getCondicion() {
        return condicion;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefonos() {
        return telefonos;
    }

    public EstadoContribuyente getEstado() {
        return estado;
    }

    public DomicilioFiscal getDomicilioFiscal() {
        return domicilioFiscal;
    }
}
