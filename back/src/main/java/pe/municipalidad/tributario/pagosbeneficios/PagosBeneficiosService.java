package pe.municipalidad.tributario.pagosbeneficios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.BeneficioTributario;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Pago;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.pagosbeneficios.PagosBeneficiosRespuesta.BeneficioVigente;
import pe.municipalidad.tributario.pagosbeneficios.PagosBeneficiosRespuesta.ContribuyenteResumen;
import pe.municipalidad.tributario.pagosbeneficios.PagosBeneficiosRespuesta.EstadoCuentaResumen;
import pe.municipalidad.tributario.pagosbeneficios.PagosBeneficiosRespuesta.PagoDetalle;
import pe.municipalidad.tributario.pagosbeneficios.PagosBeneficiosRespuesta.Resumen;

/* HU048: pagos y beneficios de un contribuyente, obtenidos de SAMNET con SamnetGateway (solo lectura). */
@Service
public class PagosBeneficiosService {

    private final SamnetGateway samnet;

    public PagosBeneficiosService(SamnetGateway samnet) {
        this.samnet = samnet;
    }

    /* codigoODocumento: código de contribuyente, DNI o RUC; se toleran espacios y minúsculas. */
    public PagosBeneficiosRespuesta consultar(String codigoODocumento) {
        String clave = codigoODocumento.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        Contribuyente contribuyente = samnet.buscarContribuyente(clave).stream()
                .filter(c -> c.getCodigoContribuyente().equalsIgnoreCase(clave)
                        || c.getNumeroDocumento().equalsIgnoreCase(clave))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("CONTRIBUYENTE_NO_ENCONTRADO",
                        "No existe un contribuyente con código o documento " + clave + "."));

        String codigo = contribuyente.getCodigoContribuyente();
        EstadoCuenta cuenta = samnet.obtenerEstadoCuenta(codigo);
        LocalDate fechaCorte = cuenta.obtenerFechaCorte();

        List<PagoDetalle> pagos = cuenta.getPagos().stream().map(PagosBeneficiosService::detalleDe).toList();
        List<BeneficioVigente> beneficios = samnet.verificarBeneficio(codigo).stream()
                .map(PagosBeneficiosService::vigenteDe).toList();

        return new PagosBeneficiosRespuesta(
                new ContribuyenteResumen(codigo, contribuyente.getTipoDocumento().name(),
                        contribuyente.getNumeroDocumento(), contribuyente.getNombreRazonSocial()),
                fechaCorte, resumenDe(pagos), estadoCuentaDe(cuenta, fechaCorte), pagos, beneficios);
    }

    private static PagoDetalle detalleDe(Pago pago) {
        return new PagoDetalle(pago.idPago(), pago.fechaPago(), pago.canal(), pago.montoPagado(),
                pago.montoCorrespondiente(), pago.diferencia(), pago.beneficioAplicado());
    }

    private static BeneficioVigente vigenteDe(BeneficioTributario beneficio) {
        return new BeneficioVigente(beneficio.idBeneficio(), beneficio.tipo(), beneficio.porcentajeDescuento(),
                beneficio.vigenciaDesde(), beneficio.vigenciaHasta());
    }

    private static Resumen resumenDe(List<PagoDetalle> pagos) {
        return new Resumen(
                pagos.size(),
                (int) pagos.stream().filter(p -> p.beneficioAplicado() != null).count(),
                suma(pagos, PagoDetalle::montoPagado),
                suma(pagos, PagoDetalle::montoCorrespondiente),
                suma(pagos, PagoDetalle::diferencia));
    }

    private static EstadoCuentaResumen estadoCuentaDe(EstadoCuenta cuenta, LocalDate fechaCorte) {
        List<Obligacion> obligaciones = cuenta.getObligaciones();
        BigDecimal emitido = obligaciones.stream().map(Obligacion::getMontoEmitido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EstadoCuentaResumen(emitido, cuenta.getTotalPagado(), cuenta.getTotalDeuda(),
                (int) obligaciones.stream().filter(o -> o.estaVencida(fechaCorte)).count());
    }

    private static BigDecimal suma(List<PagoDetalle> pagos, Function<PagoDetalle, BigDecimal> campo) {
        return pagos.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
