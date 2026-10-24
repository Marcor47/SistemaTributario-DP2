package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;

/* Contribuyente 1..* titular de * Predio, con la condición y el porcentaje de propiedad de cada titular. */
public record Titularidad(String codigoContribuyente, String condicionPropiedad, BigDecimal porcentajePropiedad) {
}
