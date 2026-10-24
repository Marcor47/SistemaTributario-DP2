package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Vista consolidada de deuda, pagos y obligaciones de un contribuyente a una fecha de corte (RF-004, RF-018).
 * desdeCopia indica que SAMNET no respondió y se armó desde la última copia guardada (RNF-002).
 */
public class EstadoCuenta {

    private final String codigoContribuyente;
    private final LocalDate fechaCorte;
    private final List<Obligacion> obligaciones;
    private final List<Pago> pagos;
    private final boolean desdeCopia;

    public EstadoCuenta(String codigoContribuyente, LocalDate fechaCorte, List<Obligacion> obligaciones,
            List<Pago> pagos, boolean desdeCopia) {
        this.codigoContribuyente = codigoContribuyente;
        this.fechaCorte = fechaCorte;
        this.obligaciones = List.copyOf(obligaciones);
        this.pagos = List.copyOf(pagos);
        this.desdeCopia = desdeCopia;
    }

    public BigDecimal getTotalDeuda() {
        return obligaciones.stream().map(Obligacion::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalPagado() {
        return obligaciones.stream().map(Obligacion::getMontoPagado).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /* Obligaciones de un tributo y año; null en cualquiera de los dos significa "todos". */
    public List<Obligacion> desglosar(Tributo tributo, Integer anio) {
        return obligaciones.stream()
                .filter(o -> tributo == null || o.getTributo() == tributo)
                .filter(o -> anio == null || o.getAnio() == anio)
                .toList();
    }

    public LocalDate obtenerFechaCorte() {
        return fechaCorte;
    }

    public String getCodigoContribuyente() {
        return codigoContribuyente;
    }

    public List<Obligacion> getObligaciones() {
        return obligaciones;
    }

    public List<Pago> getPagos() {
        return pagos;
    }

    public boolean isDesdeCopia() {
        return desdeCopia;
    }
}
