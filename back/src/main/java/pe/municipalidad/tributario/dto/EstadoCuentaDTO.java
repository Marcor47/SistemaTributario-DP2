package pe.municipalidad.tributario.dto;

import java.util.List;

public class EstadoCuentaDTO {
    private ContribuyenteDTO contribuyente;
    private ResumenDeudaDTO resumen;
    private List<ObligacionDetalleDTO> obligaciones;

    public EstadoCuentaDTO() {
    }

    public EstadoCuentaDTO(ContribuyenteDTO contribuyente, ResumenDeudaDTO resumen, List<ObligacionDetalleDTO> obligaciones) {
        this.contribuyente = contribuyente;
        this.resumen = resumen;
        this.obligaciones = obligaciones;
    }

    public ContribuyenteDTO getContribuyente() {
        return contribuyente;
    }

    public void setContribuyente(ContribuyenteDTO contribuyente) {
        this.contribuyente = contribuyente;
    }

    public ResumenDeudaDTO getResumen() {
        return resumen;
    }

    public void setResumen(ResumenDeudaDTO resumen) {
        this.resumen = resumen;
    }

    public List<ObligacionDetalleDTO> getObligaciones() {
        return obligaciones;
    }

    public void setObligaciones(List<ObligacionDetalleDTO> obligaciones) {
        this.obligaciones = obligaciones;
    }
}
