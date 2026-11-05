package pe.municipalidad.tributario.obligacion;

import java.math.BigDecimal;

/* Parte de una cuota de arbitrios que corresponde a un servicio. */
public record MontoServicio(ServicioArbitrio servicio, BigDecimal monto) {
}
