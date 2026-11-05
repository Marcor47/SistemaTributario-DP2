package pe.municipalidad.tributario.arbitrio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.arbitrio.ArbitrioPredioRespuesta.CuotaArbitrio;
import pe.municipalidad.tributario.arbitrio.ArbitrioPredioRespuesta.PredioResumen;
import pe.municipalidad.tributario.arbitrio.ArbitrioPredioRespuesta.Resumen;
import pe.municipalidad.tributario.arbitrio.ArbitrioPredioRespuesta.Titular;
import pe.municipalidad.tributario.arbitrio.ArbitrioPredioRespuesta.TotalServicio;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta.ServicioRespuesta;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.EstadoObligacion;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.ServicioArbitrio;
import pe.municipalidad.tributario.obligacion.TasaInteresMoratorio;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * HU005: arbitrios de un predio mes a mes, con su monto por servicio, estado de pago e interés moratorio si están
 * vencidos, obtenidos de SAMNET con SamnetGateway. En copropiedad cada titular tiene su parte de cada cuota.
 */
@Service
public class ArbitrioPredioService {

    private final SamnetGateway samnet;
    private final Clock reloj;
    private final TasaInteresMoratorio tim;

    public ArbitrioPredioService(SamnetGateway samnet, Clock reloj, TasaInteresMoratorio tim) {
        this.samnet = samnet;
        this.reloj = reloj;
        this.tim = tim;
    }

    public ArbitrioPredioRespuesta detalle(String codigoPredio) {
        Predio predio = samnet.buscarPredio(codigoPredio, null).stream()
                .filter(p -> p.getCodigoPredio().equals(codigoPredio))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("PREDIO_NO_ENCONTRADO",
                        "No existe un predio con código " + codigoPredio + "."));
        LocalDate fechaCorte = LocalDate.now(reloj);

        /* Los arbitrios se emiten al titular: se juntan las cuotas de cada titular sobre este predio. */
        Map<Long, Obligacion> obligaciones = new LinkedHashMap<>();
        for (Titularidad titular : predio.getTitulares()) {
            samnet.obtenerEstadoCuenta(titular.codigoContribuyente()).getObligaciones().stream()
                    .filter(o -> o.getTributo() == Tributo.ARBITRIOS && codigoPredio.equals(o.getCodigoPredio()))
                    .forEach(o -> obligaciones.putIfAbsent(o.getIdObligacion(), o));
        }

        List<CuotaArbitrio> cuotas = obligaciones.values().stream()
                .sorted(Comparator.comparingInt(Obligacion::getAnio)
                        .thenComparingInt(Obligacion::getCuota)
                        .thenComparing(Obligacion::getCodigoContribuyente))
                .map(o -> cuotaDe(o, fechaCorte, tim.mensual()))
                .toList();

        return new ArbitrioPredioRespuesta(predioResumen(predio), fechaCorte, resumenDe(cuotas), cuotas);
    }

    private PredioResumen predioResumen(Predio predio) {
        List<Titular> titulares = predio.getTitulares().stream()
                .map(t -> new Titular(t.codigoContribuyente(), nombreDe(t.codigoContribuyente()),
                        t.condicionPropiedad(), t.porcentajePropiedad()))
                .toList();
        return new PredioResumen(predio.getCodigoPredio(), predio.getUbicacion(), predio.getUsoPredio(),
                predio.getZona(), titulares);
    }

    private String nombreDe(String codigoContribuyente) {
        return samnet.buscarContribuyente(codigoContribuyente).stream()
                .filter(c -> c.getCodigoContribuyente().equals(codigoContribuyente))
                .map(Contribuyente::getNombreRazonSocial)
                .findFirst()
                .orElse(null);
    }

    private static CuotaArbitrio cuotaDe(Obligacion o, LocalDate fechaCorte, BigDecimal timMensual) {
        EstadoObligacion estado = o.estadoAl(fechaCorte);
        return new CuotaArbitrio(o.getIdObligacion(), o.getCodigoContribuyente(), o.getAnio(), o.getCuota(),
                o.etiquetaPeriodo(), o.getFechaVencimiento(), o.getMontoEmitido(), o.getMontoPagado(),
                o.getMontoDescontado(), o.saldo().max(BigDecimal.ZERO), estado,
                estado == EstadoObligacion.VENCIDA ? o.diasVencidaAl(fechaCorte) : 0,
                o.interesMoratorioAl(fechaCorte, timMensual),
                o.getServicios().stream()
                        .map(m -> new ServicioRespuesta(m.servicio().name(), m.servicio().getDescripcion(), m.monto()))
                        .toList());
    }

    private static Resumen resumenDe(List<CuotaArbitrio> cuotas) {
        return new Resumen(
                suma(cuotas, CuotaArbitrio::emitido),
                suma(cuotas, CuotaArbitrio::pagado),
                suma(cuotas, CuotaArbitrio::descontado),
                suma(cuotas, CuotaArbitrio::saldo),
                contar(cuotas, EstadoObligacion.PAGADA),
                contar(cuotas, EstadoObligacion.PENDIENTE),
                contar(cuotas, EstadoObligacion.VENCIDA),
                suma(cuotas, CuotaArbitrio::interesMoratorio),
                porServicio(cuotas));
    }

    /* Cuánto se emitió en total por cada servicio, en el orden de la ordenanza. */
    private static List<TotalServicio> porServicio(List<CuotaArbitrio> cuotas) {
        Map<String, BigDecimal> totales = new LinkedHashMap<>();
        cuotas.stream().flatMap(c -> c.servicios().stream())
                .forEach(s -> totales.merge(s.servicio(), s.monto(), BigDecimal::add));
        return Arrays.stream(ServicioArbitrio.values())
                .filter(s -> totales.containsKey(s.name()))
                .map(s -> new TotalServicio(s.name(), s.getDescripcion(), totales.get(s.name())))
                .toList();
    }

    private static BigDecimal suma(List<CuotaArbitrio> cuotas,
            Function<CuotaArbitrio, BigDecimal> campo) {
        return cuotas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int contar(List<CuotaArbitrio> cuotas, EstadoObligacion estado) {
        return (int) cuotas.stream().filter(c -> c.estado() == estado).count();
    }
}
