package pe.municipalidad.tributario.contribuyente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU001 (revisión del Sprint 1): impuestos del contribuyente que se muestran junto con su ficha, a la fecha de
 * corte de SAMNET. Se resumen por tributo y se detallan por periodo; los arbitrios van como un solo tributo.
 */
public record ImpuestosRespuesta(
        LocalDate fechaCorte,
        BigDecimal totalEmitido,
        BigDecimal totalPagado,
        BigDecimal totalDeuda,
        List<TributoResumen> porTributo,
        List<CuotaImpuesto> cuotas) {

    public record TributoResumen(
            Tributo tributo,
            String descripcion,
            BigDecimal emitido,
            BigDecimal pagado,
            BigDecimal deuda) {
    }

    public record CuotaImpuesto(
            Long idObligacion,
            Tributo tributo,
            String descripcion,
            String periodo,
            LocalDate fechaVencimiento,
            BigDecimal emitido,
            BigDecimal pagado,
            BigDecimal deuda,
            EstadoObligacion estado) {
    }

    public static ImpuestosRespuesta desde(EstadoCuenta estadoCuenta) {
        LocalDate fechaCorte = estadoCuenta.obtenerFechaCorte();
        List<Obligacion> obligaciones = estadoCuenta.getObligaciones();

        Map<Tributo, TributoResumen> porTributo = new EnumMap<>(Tributo.class);
        for (Obligacion obligacion : obligaciones) {
            porTributo.merge(obligacion.getTributo(), resumenDe(obligacion), ImpuestosRespuesta::sumar);
        }

        List<CuotaImpuesto> cuotas = obligaciones.stream()
                .sorted(Comparator.comparing(Obligacion::getFechaVencimiento)
                        .thenComparing(Obligacion::getTributo))
                .map(obligacion -> new CuotaImpuesto(
                        obligacion.getIdObligacion(),
                        obligacion.getTributo(),
                        obligacion.getTributo().getDescripcion(),
                        obligacion.etiquetaPeriodo(),
                        obligacion.getFechaVencimiento(),
                        obligacion.getMontoEmitido(),
                        obligacion.getMontoPagado(),
                        obligacion.saldo(),
                        obligacion.estadoAl(fechaCorte)))
                .toList();

        BigDecimal totalEmitido = obligaciones.stream()
                .map(Obligacion::getMontoEmitido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ImpuestosRespuesta(fechaCorte, totalEmitido, estadoCuenta.getTotalPagado(),
                estadoCuenta.getTotalDeuda(), List.copyOf(porTributo.values()), cuotas);
    }

    private static TributoResumen resumenDe(Obligacion obligacion) {
        return new TributoResumen(obligacion.getTributo(), obligacion.getTributo().getDescripcion(),
                obligacion.getMontoEmitido(), obligacion.getMontoPagado(), obligacion.saldo());
    }

    private static TributoResumen sumar(TributoResumen acumulado, TributoResumen otro) {
        return new TributoResumen(acumulado.tributo(), acumulado.descripcion(),
                acumulado.emitido().add(otro.emitido()),
                acumulado.pagado().add(otro.pagado()),
                acumulado.deuda().add(otro.deuda()));
    }
}
