package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Inmueble afecto al Impuesto Predial y a los arbitrios (RF-002, RF-048). Se obtiene de SAMNET con
 * SamnetGateway. La condición y el porcentaje de propiedad son de cada titular (titulares).
 */
public class Predio {

    private final String codigoPredio;
    private final String numeroCatastral;
    private final String ubicacion;
    private final String usoPredio;
    private final TipoPredio tipoPredio;
    private final BigDecimal areaTerreno;
    private final BigDecimal baseImponible;
    private final LocalDate fechaAdquisicion;
    private final LocalDate fechaBaja;
    private final String zona;
    private final List<Titularidad> titulares;

    public Predio(String codigoPredio, String numeroCatastral, String ubicacion, String usoPredio,
            TipoPredio tipoPredio, BigDecimal areaTerreno, BigDecimal baseImponible, LocalDate fechaAdquisicion,
            LocalDate fechaBaja, String zona, List<Titularidad> titulares) {
        this.codigoPredio = codigoPredio;
        this.numeroCatastral = numeroCatastral;
        this.ubicacion = ubicacion;
        this.usoPredio = usoPredio;
        this.tipoPredio = tipoPredio;
        this.areaTerreno = areaTerreno;
        this.baseImponible = baseImponible;
        this.fechaAdquisicion = fechaAdquisicion;
        this.fechaBaja = fechaBaja;
        this.zona = zona;
        this.titulares = List.copyOf(titulares);
    }

    /* Reglas de RF-048 que se verifican antes de enviar el registro a SAMNET; devuelve los errores. */
    public List<String> validar() {
        List<String> errores = new ArrayList<>();
        if (ubicacion == null || ubicacion.isBlank()) {
            errores.add("ubicacion: es obligatoria");
        }
        if (zona == null || zona.isBlank()) {
            errores.add("zona: es obligatoria");
        }
        if (fechaBaja != null && fechaAdquisicion != null && !fechaBaja.isAfter(fechaAdquisicion)) {
            errores.add("fechaBaja: debe ser posterior a la fecha de adquisición");
        }
        BigDecimal total = titulares.stream().map(Titularidad::porcentajePropiedad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            errores.add("porcentajePropiedad: la suma de los titulares supera 100");
        }
        return errores;
    }

    public boolean tieneTitular(String codigoContribuyente) {
        return titulares.stream().anyMatch(t -> t.codigoContribuyente().equals(codigoContribuyente));
    }

    public String getCodigoPredio() {
        return codigoPredio;
    }

    public String getNumeroCatastral() {
        return numeroCatastral;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public String getUsoPredio() {
        return usoPredio;
    }

    public TipoPredio getTipoPredio() {
        return tipoPredio;
    }

    public BigDecimal getAreaTerreno() {
        return areaTerreno;
    }

    public BigDecimal getBaseImponible() {
        return baseImponible;
    }

    public LocalDate getFechaAdquisicion() {
        return fechaAdquisicion;
    }

    public LocalDate getFechaBaja() {
        return fechaBaja;
    }

    public String getZona() {
        return zona;
    }

    public List<Titularidad> getTitulares() {
        return titulares;
    }
}
